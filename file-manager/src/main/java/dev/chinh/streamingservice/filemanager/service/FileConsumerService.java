package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.config.ApplicationConfig;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.event.FileEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileConsumerService {

    private final MongoTemplate mongoTemplate;
    private final FileService fileService;
    private final DirectoryCacheService directoryCacheService;
    private final ApplicationEventPublisher publisher;

    public void handleCreateFile(MediaUpdateEvent.FileCreated event) {
        String rootId = fileService.getROOT_FOLDER_ID();
        StringBuilder currentPath = new StringBuilder("/" + rootId + "/");
        String[] parts = event.fileName().split("/");
        String parentId = rootId;
        if (parts.length > 1) {
            for (int i = 0; i < parts.length - 1; i++) {
                ApplicationConfig.DirectoryCached directoryCached = directoryCacheService.getCachedOrCreateDirectory(parts[i], parentId, currentPath.toString(), event.userId());

                directoryCacheService.addDirectoryToUserUsingList(event.userId(), parts[i], parentId);

                String folderId = directoryCached.dirId();
                parentId = folderId;
                currentPath.append(folderId).append("/");
            }
        }
        String fileName = parts[parts.length - 1];
        FileSystemItem fileItem = FileSystemItem.builder()
                .userId(Long.parseLong(event.userId()))
                .parentId(parentId)
                .path(currentPath.toString())
                .bucket(event.bucket())
                .objectName(event.objectName())
                .name(fileName)
                .size(event.size())
                .fileType(FileType.detectFileTypeFromMediaType(MediaType.detectMediaType(fileName)))
                .uploadDate(Instant.now())
                .build();
        mongoTemplate.insert(fileItem);

        if (event.isLast()) {
            directoryCacheService.removeAllDirectoriesUserUsing(event.userId());
        }

        if (event.mediaId() != null && event.mediaType() != null) {
            publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.MediaEnriched(
                            event.userId(),
                            fileItem.getId(),
                            event.mediaId(),
                            event.mediaType(),
                            event.thumbnailObject(),
                            true,
                            -1,
                            -1
                    )
            ));
        }
    }

    public void handleDirectoryToMedia(MediaUpdateEvent.DirectoryToMediaInitiated event) {
        FileSystemItem item = fileService.findById(event.userId(), event.fileId());
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
                        .where(FileItemField.PATH).regex("^" + parentPath))
                .limit(batchSize)
                .skip(skip);
        query.fields().include("id", FileItemField.SIZE);
        List<FileSystemItem> batch = mongoTemplate.find(query, FileSystemItem.class);

        size += batch.stream()
                .filter(f -> FileType.isNotDir(f.getFileType()))
                .mapToLong(FileSystemItem::getSize).sum();

        boolean hasMore = batch.size() == batchSize;
        skip += batch.size();

        if (hasMore) {
            publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    new MediaUpdateEvent.DirectoryToMediaInitiated(
                            event.userId(), event.fileId(), event.mediaId(), event.mediaType(), event.searchable(), false, event.thumbnailObject(), size, skip)
            ));
            return;
        }

        if (event.updateParentLength()) {
            Update update = new Update().inc(FileItemField.LENGTH, 1);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(item.getParentId())),
                    update,
                    FileSystemItem.class);
        }

        Update update = new Update().set(FileItemField.SIZE, size);
        if (event.thumbnailObject() != null)
            update.set(FileItemField.THUMBNAIL, event.thumbnailObject());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(item.getId())),
                update,
                FileSystemItem.class);

        publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                EventTopics.MEDIA_OBJECT_TOPIC,
                new MediaUpdateEvent.MediaEnriched(
                        event.userId(), item.getId(), event.mediaId(), event.mediaType(), event.thumbnailObject(), event.searchable(), size, skip)
        ));
    }

    public void handleNestedDirectoryToMedia(MediaUpdateEvent.NestedDirectoryToMediaInitiated event) {
        FileSystemItem item = fileService.findById(event.userId(), event.fileId());
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
        Query query = Query.query(Criteria.where(FileItemField.PARENT_ID).is(item.getId()))
                .with(Sort.by(Sort.Direction.ASC, FileItemField.NAME))
                .limit(batchSize)
                .skip(skip);
        List<FileSystemItem> directChildren = mongoTemplate.find(query, FileSystemItem.class);
        for (FileSystemItem child : directChildren) {
            if (child.getFileType() == FileType.DIR) {
                publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                        EventTopics.MEDIA_UPLOAD_TOPIC,
                        new MediaUpdateEvent.FileToMediaInitiated(
                                event.userId(),
                                child.getId(), event.childType(),
                                null, null,
                                child.getName(), child.getUploadDate(),
                                event.mediaId(), child.getMId(),
                                event.childSearchable(), false)
                ));
            }
        }

        boolean hasMore = directChildren.size() == batchSize;
        skip += directChildren.size();

        if (hasMore) {
            publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    new MediaUpdateEvent.NestedDirectoryToMediaInitiated(
                            event.userId(), item.getId(), event.mediaId(), event.parentType(), event.childType(), event.childSearchable(), event.thumbnailObject(), skip)
            ));
            return;
        }

        publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                EventTopics.MEDIA_OBJECT_TOPIC,
                new MediaUpdateEvent.MediaEnriched(
                        event.userId(), item.getId(), event.mediaId(), event.parentType(), event.thumbnailObject(), true, -1, skip)
        ));
    }

    public UpdateResult handleCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        return fileService.updateFileMetadataAsMedia(
                event.userId(),
                event.fileId(),
                event.mediaId(),
                FileType.detectFileTypeFromMediaType(event.mediaType()),
                event.thumbnail(),
                event.length(),
                event.width(),
                event.height()
        );
    }

    public void handleInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        FileSystemItem item = fileService.findByMId(event.userId(), event.mediaId());
        if (item == null) {
            System.err.println("Item not found, skipping update media thumbnail");
            return;
        }
        String objectName = item.getObjectName();
        String bucket = item.getBucket();
        if (!FileType.isNotDir(item.getFileType())) {
            String parentPath = Pattern.quote(fileService.getPathForFileItem(item.getPath(), item.getId()));
            Query query = Query.query(Criteria
                            .where(FileItemField.PATH).regex("^" + parentPath)
                            .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO))
                    .with(Sort.by(Sort.Direction.ASC, FileItemField.NAME))
                    .skip(event.num() - 1)
                    .limit(1);
            FileSystemItem numItem = mongoTemplate.findOne(query, FileSystemItem.class);
            if (numItem == null) {
                System.err.println("Item with id: " + item.getId() + " does not have child at num " + event.num() + ", skipping update media thumbnail");
                return;
            }
            objectName = numItem.getObjectName();
            bucket = numItem.getBucket();
        }

        publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                EventTopics.MEDIA_OBJECT_TOPIC,
                new MediaUpdateEvent.MediaThumbnailUpdated(
                        event.userId(),
                        item.getMId(),
                        event.mediaType(),
                        (double) event.num(),
                        bucket,
                        objectName)
        ));
    }

    public void handleUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        Query query = Query.query(Criteria.where(FileItemField.MEDIA_ID).is(event.mediaId()));
        Update update = new Update()
                .set(FileItemField.THUMBNAIL, event.newThumbnail());
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    public void handleDeleteFile(MediaUpdateEvent.FileDeleted event) {
        FileSystemItem fileItem = fileService.findById(event.userId(), event.fileId());
        if (fileItem == null) {
            System.out.println("File not found with id: " + event.fileId() + ", skipping delete");
            return;
        }

        boolean isMedia = fileItem.getMId() != null && fileItem.getMId() > 0;
        if (isMedia && fileItem.getFileType() == FileType.ALBUM) {
            FileSystemItem parent = fileService.findById(event.userId(), fileItem.getParentId());
            if (parent != null && parent.getFileType() == FileType.GROUPER) {
                Query query = new Query(Criteria.where("id").is(parent.getId()));
                Update update = new Update().set(FileItemField.LENGTH, parent.getLength() - 1);
                mongoTemplate.updateFirst(query, update, FileSystemItem.class);
            }
        }

        deleteFile(fileItem);
    }

    private void deleteFile(FileSystemItem fileItem) {
        boolean hasMore = !FileType.isNotDir(fileItem.getFileType());

        int batchSize = 500;
        while (hasMore) {
            String parentPath = Pattern.quote(fileService.getPathForFileItem(fileItem.getPath(), fileItem.getId()));
            List<FileSystemItem> batch = mongoTemplate.find(Query.query(Criteria
                    .where(FileItemField.PATH).regex("^" + parentPath)).limit(batchSize), FileSystemItem.class);

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
                publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                        EventTopics.MEDIA_OBJECT_TOPIC,
                        new MediaUpdateEvent.ObjectDeleted(entry.getKey(), entry.getValue())
                ));
            }
            mongoTemplate.remove(new Query(Criteria.where("id").in(ids)), FileSystemItem.class);
            System.out.println("Deleted " + ids.size() + " items");
        }

        mongoTemplate.remove(new Query(Criteria.where("id").is(fileItem.getId())), FileSystemItem.class);
        publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                EventTopics.MEDIA_OBJECT_TOPIC,
                new MediaUpdateEvent.ObjectDeleted(fileItem.getBucket(), Collections.singletonList(fileItem.getObjectName()))
        ));
        if (fileItem.getThumbnail() != null)
            publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                    EventTopics.MEDIA_OBJECT_AND_BACKUP_TOPIC,
                    new MediaUpdateEvent.ThumbnailDeleted(fileItem.getThumbnail())
            ));
    }

    public void handleMoveDirectory(String userId, String fileId, String newParentId, String oldPath) {
        FileSystemItem item = fileService.findById(userId, fileId);
        if (item == null) {
            System.err.println("File not found. Skipping...");
            return;
        }
        if (FileType.isNotDir(item.getFileType())) {
            System.err.println("File is not a directory. Moving single file is handled at initiation already. Skipping...");
            return;
        }
        FileSystemItem newParent = fileService.findById(userId, newParentId);
        if (newParent == null) {
            System.err.println("Parent not found. Skipping...");
            return;
        }
        if (FileType.isNotDir(newParent.getFileType())) {
            System.err.println("Parent is not a directory. Skipping...");
            return;
        }

        // needing oldPath since item or source dir path is already updated to reflect changes
        // item is the source directory, we need to get all children and update their paths
        String childrenIdPrefix = oldPath + item.getId() + "/"; // all children in the directory
        String newIdPrefix = newParent.getPath() + newParent.getId() + "/";

        String anchoredRegex = "^" + Pattern.quote(childrenIdPrefix);
        Query query = new Query(Criteria.where(FileItemField.PATH).regex(anchoredRegex)); // find children
        AggregationUpdate update = AggregationUpdate.update()
                .set(FileItemField.PATH)
                .toValue(StringOperators.ReplaceOne.valueOf(FileItemField.PATH)
                        .find(oldPath) // find old path prefix and replace with new path prefix
                        .replacement(newIdPrefix));
        mongoTemplate.updateMulti(query, update, FileSystemItem.class);

        Set<String> commonIds = fileService.getCommonIds(oldPath + item.getId() + newParent.getPath() + newParent.getId());
        fileService.releaseLockedFileItem(commonIds);
    }
}
