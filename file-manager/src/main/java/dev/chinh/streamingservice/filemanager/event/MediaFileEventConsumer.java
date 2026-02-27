package dev.chinh.streamingservice.filemanager.event;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.config.KafkaConfig;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.service.FileService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
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
import java.util.*;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class MediaFileEventConsumer {

    private final MongoTemplate mongoTemplate;
    private final FileService fileService;
    private final MediaFileEventProducer producer;

    private void onCreateFile(MediaUpdateEvent.FileCreated event) {
        System.out.println("Received create file event");
        try {
            HashMap<String, String> folderIdMap = new HashMap<>();

            String rootId = fileService.getROOT_FOLDER_ID();
            String currentPath = "/" + rootId + "/";
            String[] parts = event.fileName().split("/");
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
                    currentPath += folderId + "/";
                }
            }
            String fileName = parts[parts.length - 1];
            FileSystemItem fileItem = FileSystemItem.builder()
                    .parentId(parentId)
                    .path(currentPath)
                    .bucket(event.bucket())
                    .objectName(event.objectName())
                    .name(fileName)
                    .size(event.size())
                    .fileType(FileType.detectFileTypeFromMediaType(MediaType.detectMediaType(fileName)))
                    .uploadDate(Instant.now())
                    .build();
            mongoTemplate.insert(fileItem);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create file", e);
        }
    }

    private void onDirectoryToMedia(MediaUpdateEvent.DirectoryToMediaInitiated event) {
        System.out.println("Received initiate directory to media initiated event");
        try {
            FileSystemItem item = fileService.findById(event.fileId());
            if (item == null) {
                System.out.println("Item not found, skipping directory to media");
                return;
            }
            if (item.getFileType() == FileType.ALBUM || item.getFileType() == FileType.GROUPER) {
                System.out.println("Item is already Album or Grouper, skipping directory to media");
                return;
            }
            if (item.getFileType() != FileType.DIR) {
                System.out.println("Item is not a directory, skipping directory to media");
                return;
            }
            long size = event.initialSize();
            int skip = event.offset();
            int batchSize = 1000;

            String parentPath = Pattern.quote(fileService.getPathForFileItem(item.getPath(), item.getId()));
            Query query = Query.query(Criteria
                            .where("path").regex("^" + parentPath))
                    .limit(batchSize)
                    .skip(skip);
            query.fields().include("id", "size");
            List<FileSystemItem> batch = mongoTemplate.find(query, FileSystemItem.class);

            size += batch.stream()
                    .filter(f -> FileType.isNotDir(f.getFileType()))
                    .mapToLong(FileSystemItem::getSize).sum();

            boolean hasMore = batch.size() == batchSize;
            skip += batch.size();

            if (hasMore) {
                producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        new MediaUpdateEvent.DirectoryToMediaInitiated(
                                event.fileId(), event.mediaId(), event.mediaType(), event.searchable(), event.thumbnailObject(), size, skip)
                ));
                return;
            }

            Update update = new Update().set("size", size);
            if (event.thumbnailObject() != null)
                update.set("thumbnail", event.thumbnailObject());
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getId())),
                    update,
                    FileSystemItem.class);

            producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.MediaEnriched(
                            event.mediaId(), event.mediaType(), event.thumbnailObject(), event.searchable(), item.getId(), size, skip)
            ));
        } catch (Exception e) {
            System.err.println("Failed to initiate directory to media");
            throw e;
        }
    }

    private void onNestedDirectoryToMedia(MediaUpdateEvent.NestedDirectoryToMediaInitiated event) {
        System.out.println("Received initiate nested directory to media initiated event");
        try {
            FileSystemItem item = fileService.findById(event.fileId());
            if (item == null) {
                System.out.println("Item not found, skipping nested directory to media");
                return;
            }
            if (item.getFileType() == FileType.ALBUM || item.getFileType() == FileType.GROUPER) {
                System.out.println("Item is already Album or Grouper, skipping directory to media");
                return;
            }
            if (item.getFileType() != FileType.DIR) {
                System.out.println("Item is not a directory, skipping directory to media");
                return;
            }

            int batchSize = 1000;
            int skip = event.offset();
            Query query = Query.query(Criteria.where("parentId").is(item.getId()))
                    .with(Sort.by(Sort.Direction.ASC, "name"))
                    .limit(batchSize)
                    .skip(skip);
            List<FileSystemItem> directChildren = mongoTemplate.find(query, FileSystemItem.class);
            for (FileSystemItem child : directChildren) {
                if (child.getFileType() == FileType.DIR) {
                    int childIndex = directChildren.indexOf(child) + skip;
                    producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                            EventTopics.MEDIA_UPLOAD_TOPIC,
                            new MediaUpdateEvent.FileToMediaInitiated(
                                    child.getId(), event.childType(), null, null, child.getName(), child.getUploadDate(), event.mediaId(), childIndex)
                    ));
                }
            }

            boolean hasMore = directChildren.size() == batchSize;
            skip += directChildren.size();

            if (hasMore) {
                producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        new MediaUpdateEvent.NestedDirectoryToMediaInitiated(
                                item.getId(), event.mediaId(), event.parentType(), event.childType(), event.childSearchable(), event.thumbnailObject(), skip)
                ));
                return;
            }

            Query updateQuery = Query.query(Criteria.where("id").is(item.getId()));
            Update update = new Update()
                    .set("length", skip + directChildren.size());
            if (event.thumbnailObject() != null)
                update.set("thumbnail", event.thumbnailObject());
            mongoTemplate.updateFirst(updateQuery, update, FileSystemItem.class);
        } catch (Exception e) {
            System.err.println("Failed to initiate nested directory to media");
            throw e;
        }
    }

    private void onCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        System.out.println("Received media create event from file: " + event.mediaId());
        try {
            UpdateResult result = fileService.updateFileMetadataAsMedia(
                    event.fileId(),
                    event.mediaId(),
                    FileType.detectFileTypeFromMediaType(event.mediaType()),
                    event.thumbnail(),
                    event.length()
            );
            if (result.getModifiedCount() != 1)
                throw new RuntimeException("Failed to update file to media");
        } catch (Exception e) {
            System.err.println("Failed to update file to media");
            throw e;
        }
    }

    private void onInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        System.out.println("Received initiate update media thumbnail event");
        try {
            FileSystemItem item = fileService.findByMId(event.mediaId());
            if (item == null) {
                System.out.println("Item not found, skipping update media thumbnail");
                return;
            }
            String objectName = item.getObjectName();
            String bucket = item.getBucket();
            if (!FileType.isNotDir(item.getFileType())) {
                String parentPath = Pattern.quote(fileService.getPathForFileItem(item.getPath(), item.getId()));
                Query query = Query.query(Criteria
                                .where("path").regex("^" + parentPath)
                                .and("fileType").in(FileType.IMAGE, FileType.VIDEO))
                        .with(Sort.by(Sort.Direction.ASC, "name"))
                        .skip(event.num() - 1)
                        .limit(1);
                FileSystemItem numItem = mongoTemplate.findOne(query, FileSystemItem.class);
                if (numItem == null) {
                    System.out.println("Item with id: " + item.getId() + " does not have child at num " + event.num() + ", skipping update media thumbnail");
                    return;
                }
                objectName = numItem.getObjectName();
                bucket = numItem.getBucket();
            }

            producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.MediaThumbnailUpdated(
                            item.getMId(),
                            event.mediaType(),
                            (double) event.num(),
                            bucket,
                            objectName)
            ));
        } catch (Exception e) {
            System.err.println("Failed to initiate update media thumbnail");
            throw e;
        }
    }

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        System.out.println("Received update media thumbnail name: " + event.mediaId());
        try {
            Query query = Query.query(Criteria.where("mId").is(event.mediaId()));
            Update update = new Update()
                    .set("thumbnail", event.newThumbnail());
            mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update thumbnail name for media " + event.mediaId(), e);
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

    private void onDeleteFile(MediaUpdateEvent.FileDeleted event) {
        System.out.println("Received file delete event: " + event.fileId());
        try {
            FileSystemItem fileItem = fileService.findById(event.fileId());
            if (fileItem == null) {
                System.out.println("File not found with id: " + event.fileId() + ", skipping delete");
                return;
            }

            boolean isMedia = fileItem.getMId() != null && fileItem.getMId() > 0;
            if (isMedia && fileItem.getFileType() == FileType.ALBUM) {
                FileSystemItem parent = fileService.findById(fileItem.getParentId());
                if (parent != null && parent.getFileType() == FileType.GROUPER) {
                    Query query = new Query(Criteria.where("id").is(parent.getId()));
                    Update update = new Update().set("length", parent.getLength() - 1);
                    mongoTemplate.updateFirst(query, update, FileSystemItem.class);
                }
            }

            deleteFile(fileItem);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private void deleteFile(FileSystemItem fileItem) {
        boolean hasMore = !FileType.isNotDir(fileItem.getFileType());

        int batchSize = 500;
        while (hasMore) {
            String parentPath = Pattern.quote(fileService.getPathForFileItem(fileItem.getPath(), fileItem.getId()));
            List<FileSystemItem> batch = mongoTemplate.find(Query.query(Criteria
                    .where("path").regex("^" + parentPath)).limit(batchSize), FileSystemItem.class);

            hasMore = batch.size() == batchSize;

            List<String> ids = new ArrayList<>();
            Map<String, List<String>> toDelete = new HashMap<>();
            for (FileSystemItem item : batch) {
                ids.add(item.getId());
                toDelete.computeIfAbsent(item.getBucket(), _ -> new ArrayList<>()).add(item.getObjectName());
                if (item.getThumbnail() != null)
                    toDelete.computeIfAbsent(ContentMetaData.THUMBNAIL_BUCKET, _ -> new ArrayList<>()).add(item.getThumbnail());
            }
            for (Map.Entry<String, List<String>> entry : toDelete.entrySet()) {
                producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                        EventTopics.MEDIA_OBJECT_TOPIC,
                        new MediaUpdateEvent.ObjectDeleted(entry.getKey(), entry.getValue())
                ));
            }
            mongoTemplate.remove(new Query(Criteria.where("id").in(ids)), FileSystemItem.class);
            System.out.println("Deleted " + ids.size() + " items");
        }

        mongoTemplate.remove(new Query(Criteria.where("id").is(fileItem.getId())), FileSystemItem.class);
        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_OBJECT_TOPIC,
                new MediaUpdateEvent.ObjectDeleted(fileItem.getBucket(), Collections.singletonList(fileItem.getObjectName()))
        ));
        if (fileItem.getThumbnail() != null)
            producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.ObjectDeleted(ContentMetaData.THUMBNAIL_BUCKET, Collections.singletonList(fileItem.getThumbnail()))
            ));
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_FILE_TOPIC,
            EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_BACKUP_TOPIC
    }, groupId = KafkaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.FileCreated e -> onCreateFile(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteFile(e);
                case MediaUpdateEvent.DirectoryToMediaInitiated e -> onDirectoryToMedia(e);
                case MediaUpdateEvent.NestedDirectoryToMediaInitiated e -> onNestedDirectoryToMedia(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCompleteFileToMedia(e);
                case MediaUpdateEvent.MediaThumbnailUpdateInitiated e -> onInitiateUpdateMediaThumbnail(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);
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
            case MediaUpdateEvent.FileCreated e ->
                System.out.println("Received create file event: " + e.objectName());
            case MediaUpdateEvent.DirectoryToMediaInitiated e ->
                System.out.println("Received initiate directory to media initiated event: " + e.fileId());
            case MediaUpdateEvent.NestedDirectoryToMediaInitiated e ->
                System.out.println("Received initiate nested directory to media initiated event: " + e.fileId());
            case MediaUpdateEvent.MediaCreatedReady e ->
                System.out.println("Received create media event: " + e.mediaId());
            case MediaUpdateEvent.FileDeleted e ->
                System.out.println("Received file delete event: " + e.fileId());
            case MediaUpdateEvent.MediaThumbnailUpdateInitiated e ->
                System.out.println("Received initiate update media thumbnail event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                System.out.println("Received update media thumbnail name: " + e.mediaId());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge();
            }
        }
        System.out.println("======= =======");
    }
}
