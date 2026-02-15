package dev.chinh.streamingservice.filemanager.event;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.*;
import dev.chinh.streamingservice.filemanager.config.KafkaConfig;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class MediaFileEventConsumer {

    private final MongoTemplate mongoTemplate;
    private final MinIOService minIOService;
    private final FileService fileService;

    private void onCreateMediaFile(MediaUpdateEvent.MediaFileCreated event) {
        System.out.println("Received unfinished media create event");
        try {
            HashMap<String, String> folderIdMap = new HashMap<>();

            String rootId = fileService.getROOT_FOLDER_ID();
            for (String fileName : event.objectNames()) {
                String currentPath = "/" + fileService.getRootFolderName() + "/";
                String[] parts = fileName.split("/");
                String parentId = rootId;
                if (parts.length > 1) {
                    for (int i = 0; i < parts.length - 1; i++) {
                        String key = parts[i] + "|" + parentId;
                        String folderId = folderIdMap.getOrDefault(
                                key,
                                getOrCreateFolder(parts[i], parentId, currentPath, FileType.DIR)
                        );
                        folderIdMap.putIfAbsent(key, folderId);
                        parentId = folderId;
                        currentPath += parts[i] + "/";
                    }
                }
                String objectName = parts[parts.length - 1];
                FileSystemItem fileItem = FileSystemItem.builder()
                        .parentId(parentId)
                        .path(currentPath)
                        .name(objectName)
                        .fileType(FileType.detectFileTypeFromMediaType(MediaType.detectMediaType(objectName)))
                        .uploadDate(Instant.now())
                        .build();
                mongoTemplate.insert(fileItem);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create unfinished media file", e);
        }
    }

    private void onCreateMedia(MediaUpdateEvent.MediaCreatedReady event) {
        if (event.fileId() != null) {
            onCompleteFileToMedia(event);
            return;
        }
        System.out.println("Received media create event: " + event.mediaId());
        try {
            String rootId = fileService.getROOT_FOLDER_ID();
            String currentPath = "/" + fileService.getRootFolderName() + "/";
            String parentId = rootId;
            String[] parts = event.path().split("/");
            if (parts.length > 1) {
                int end = event.mediaType() == MediaType.VIDEO ? parts.length - 1 : parts.length;
                for (int i = 0; i < end; i++) {
                    parentId = getOrCreateFolder(parts[i], parentId, currentPath, end == parts.length ? FileType.ALBUM : FileType.DIR);
                    currentPath += parts[i] + "/";
                }
            }
            FileSystemItem fileItem = FileSystemItem.builder()
                    .parentId(parentId)
                    .path(currentPath)
                    .mId(event.mediaId())
                    .name(parts[parts.length - 1])
                    .thumbnail(event.thumbnail())
                    .size(event.size())
                    .uploadDate(event.uploadDate())
                    .build();
            if (event.mediaType() == MediaType.VIDEO) {
                fileItem.setFileType(FileType.VIDEO);
            } else if (event.mediaType() == MediaType.ALBUM) {
                fileItem.setFileType(FileType.ALBUM);
            }
            FileSystemItem saved = mongoTemplate.insert(fileItem);

            if (event.mediaType() == MediaType.ALBUM) {
                Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(event.bucket(), event.path());
                for (Result<Item> result : results) {
                    String objectName = result.get().objectName();
                    String fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
                    FileSystemItem albumItem = FileSystemItem.builder()
                            .parentId(saved.getId())
                            .path(saved.getPath() + saved.getName() + '/')
                            .fileType(FileType.detectFileTypeFromMediaType(MediaType.detectMediaType(fileName)))
                            .name(fileName)
                            .size(result.get().size())
                            .uploadDate(Instant.now())
                            .build();
                    mongoTemplate.insert(albumItem);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create media file", e);
        }
    }

    private void onCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        System.out.println("Received media create event from file: " + event.mediaId());
        try {
            UpdateResult result = fileService.updateFileMetadataAsMedia(
                    event.fileId(),
                    event.mediaId(),
                    FileType.detectFileTypeFromMediaType(event.mediaType()),
                    event.thumbnail()
            );
            if (result.getModifiedCount() != 1)
                throw new RuntimeException("Failed to update file to media");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create media from file", e);
        }
    }

    private String getOrCreateFolder(String name, String parentId, String currentPath, FileType fileType) {
        Query query = new Query(Criteria
                .where("parentId").is(parentId)
                .and("name").is(name)
                .and("fileType").in(FileType.DIR, FileType.ALBUM)
        );

        Update update = new Update()
                .setOnInsert("name", name)
                .setOnInsert("parentId", parentId)
                .setOnInsert("path", currentPath)
                .setOnInsert("fileType", fileType)
                .setOnInsert("uploadDate", LocalDateTime.now());

        // upsert to create and return in one operation - atomic
        FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);

        FileSystemItem dir = mongoTemplate.findAndModify(query, update, options, FileSystemItem.class);

        if (dir == null) throw new RuntimeException("Failed to create folder");

        return dir.getId();
    }

    private void onDeleteMedia(MediaUpdateEvent.MediaDeleted event) {
        System.out.println("Received media delete event: " + event.mediaId());
        try {
            if (event.mediaType() == MediaType.ALBUM) {
                String parentPath = Pattern.quote(fileService.getPathForFileItem(event.path()));
                // remove all children where path starts with parentPath
                mongoTemplate.remove(new Query(Criteria
                        .where("path").regex("^" + parentPath)), FileSystemItem.class);
            }
            mongoTemplate.remove(new Query(Criteria.where("mId").is(event.mediaId())), FileSystemItem.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete media", e);
        }
    }

    private void onDeleteMediaFile(MediaUpdateEvent.MediaFileDeleted event) {
        System.out.println("Received media file delete event: " + event.fileId());
        try {
            FileSystemItem fileItem = mongoTemplate.findById(event.fileId(), FileSystemItem.class);
            if (fileItem == null) return;
            if (fileItem.getFileType() == FileType.DIR) {
                String parentPath = Pattern.quote(fileService.getPathForFileItem(fileItem.getPath()));
                // remove all children where path starts with parentPath
                mongoTemplate.remove(new Query(Criteria
                        .where("path").regex("^" + parentPath)), FileSystemItem.class);
            }
            mongoTemplate.remove(new Query(Criteria.where("id").is(event.fileId())));
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete media file", e);
        }
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_ALL_TOPIC,
            EventTopics.MEDIA_FILE_TOPIC,
            EventTopics.MEDIA_SEARCH_BACKUP_AND_FILE_TOPIC
    }, groupId = KafkaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaFileCreated e -> onCreateMediaFile(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCreateMedia(e);
                case MediaUpdateEvent.MediaDeleted e -> onDeleteMedia(e);
                case MediaUpdateEvent.MediaFileDeleted e -> onDeleteMediaFile(e);
                default ->
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaConfig.MEDIA_FILE_DLQ_TOPIC,
            groupId = "media-file-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        String message = errorMessage != null ? new String(errorMessage) : "No error message found";
        System.out.printf("Error Message: %s\n", message);

        switch (event) {
            case MediaUpdateEvent.MediaFileCreated e ->
                System.out.println("Received unfinished media create event: " + e.objectNames().getFirst());
            case MediaUpdateEvent.MediaCreatedReady e ->
                System.out.println("Received media create event: " + e.mediaId());
            default ->
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
        }
        System.out.println("======= =======");
    }
}
