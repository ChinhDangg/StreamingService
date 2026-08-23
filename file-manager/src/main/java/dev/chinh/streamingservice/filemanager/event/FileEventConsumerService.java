package dev.chinh.streamingservice.filemanager.event;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.config.ApplicationConfig;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.service.DirectoryCacheService;
import dev.chinh.streamingservice.filemanager.service.FileCacheService;
import dev.chinh.streamingservice.filemanager.service.FileLockService;
import dev.chinh.streamingservice.filemanager.service.FileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileEventConsumerService {

    private static final Logger log = LoggerFactory.getLogger(FileEventConsumerService.class);
    private final MongoTemplate mongoTemplate;
    private final FileService fileService;
    private final FileCacheService fileCacheService;
    private final DirectoryCacheService directoryCacheService;
    private final FileLockService fileLockService;
    private final ApplicationEventPublisher publisher;

    @Transactional
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
        var savedFileItem = mongoTemplate.insert(fileItem);

        if (event.isLast()) {
            directoryCacheService.removeAllDirectoriesUserUsing(event.userId());
        }

        Set<String> parentIds = fileService.getCommonIds(savedFileItem.getPath());
        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> parents = fileService.getItemInIds(parentIds, true, criteria, f -> f.getFileType() == FileType.ALBUM);
        updateParentMediaLength(event.userId(), parents, 1, true);

        if (event.addAsVideo()) {
            fileService.addFileAsVideoMedia(String.valueOf(savedFileItem.getUserId()), savedFileItem.getId(), event.nameUpdateListAsJson());
        }

        log.info("Created file: {} with name: {}", savedFileItem.getId(), savedFileItem.getName());
    }



    @Transactional
    public void handleNestedDirectoryToGrouperMedia(MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated event) {
        FileSystemItem item = fileService.findById(event.userId(), event.fileId(), true);
        if (item == null) {
            log.warn("Item not found, skipping nested directory to media {}", event.fileId());
            return;
        }
        if (item.getFileType() == FileType.ALBUM || item.getFileType() == FileType.GROUPER) {
            log.warn("Item is already Album or Grouper, skipping nested directory to media {}", event.fileId());
            return;
        }
        if (item.getFileType() != FileType.DIR) {
            log.warn("Item is not a directory, skipping nested directory to media {}", event.fileId());
            return;
        }
        if (event.mediaId() == null) {
            log.warn("Media id is null, skipping nested directory to media {}", event.fileId());
            return;
        }

        int batchSize = 1000;
        int skip = event.length();

        String bucket = event.bucket();
        String objectName = event.objectName();
        if (bucket == null && objectName == null && event.length() == 0) {
            FileSystemItem first = findFirstImageOrVideo(event.userId(), fileService.getPathForFileItem(item.getPath(), item.getId()));
            bucket = first.getBucket();
            objectName = first.getObjectName();
        }

        Query query = Query.query(Criteria.where(FileItemField.PARENT_ID).is(item.getId()))
                .with(Sort.by(Sort.Direction.ASC, FileItemField.NAME))
                .limit(batchSize)
                .skip(skip);
        List<FileSystemItem> directChildren = mongoTemplate.find(query, FileSystemItem.class);
        for (FileSystemItem child : directChildren) {
            if (child.getFileType() == FileType.DIR) {
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        event.userId(),
                        new MediaUpdateEvent.DirectoryToAlbumMediaInitiated(
                                event.userId(),
                                child.getId(),
                                null, null,
                                child.getName(), child.getUploadDate(),
                                event.childSearchable(),
                                0, 0,
                                event.mediaId()
                        )
                ));
            } else if (child.getFileType() == FileType.ALBUM || child.getFileType() == FileType.GROUPER) {
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_HANDLER_TOPIC,
                        event.userId(),
                        new MediaUpdateEvent.GrouperItemMoved(
                                event.userId(),
                                child.getMId(),
                                event.mediaId(),
                                child.getName(),
                                true,
                                null
                        )
                ));
            }
        }

        boolean hasMore = directChildren.size() == batchSize;
        skip += directChildren.size();

        if (hasMore) {
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    event.userId(),
                    new MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated(
                            event.userId(),
                            event.fileId(),
                            event.mediaId(),
                            bucket, objectName,
                            event.fileName(), event.uploadDate(),
                            event.childSearchable(), event.childMediaType(),
                            skip
                    )
            ));
            return;
        }

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_HANDLER_TOPIC,
                event.userId(),
                new MediaUpdateEvent.GrouperMediaCreatedReady(
                        event.userId(),
                        event.fileId(),
                        event.mediaId(),
                        skip,
                        bucket, objectName
                )
        ));
        log.info("Created media for nested directory: {} with name: {}", item.getId(), item.getName());
    }

    @Transactional
    public void handleDirectoryToAlbumMedia(MediaUpdateEvent.DirectoryToAlbumMediaInitiated event) {
        FileSystemItem item = fileService.findById(event.userId(), event.fileId(), true);
        if (item == null) {
            log.warn("Item not found, skipping directory to media {}", event.fileId());
            return;
        }
        if (item.getFileType() == FileType.ALBUM || item.getFileType() == FileType.GROUPER) {
            log.warn("Item is already Album or Grouper, skipping directory to media {}", event.fileId());
            return;
        }
        if (item.getFileType() != FileType.DIR) {
            log.warn("Item is not a directory, skipping directory to media {}", event.fileId());
            return;
        }
        long size = event.size();
        int skip = event.length();
        int batchSize = 1000;

        String bucket = event.bucket();
        String objectName = event.objectName();
        if (bucket == null && objectName == null && event.size() == 0) {
            FileSystemItem first = findFirstImageOrVideo(event.userId(), fileService.getPathForFileItem(item.getPath(), item.getId()));
            if (first != null) {
                bucket = first.getBucket();
                objectName = first.getObjectName();
            }
        }

        String parentPath = Pattern.quote(fileService.getPathForFileItem(item.getPath(), item.getId()));
        Query query = Query.query(Criteria
                        .where(FileItemField.PATH).regex("^" + parentPath))
                .limit(batchSize)
                .skip(skip);
        query.fields().include("id", FileItemField.SIZE, FileItemField.FILE_TYPE);
        List<FileSystemItem> batch = mongoTemplate.find(query, FileSystemItem.class);

        size += batch.stream()
                .filter(f -> FileType.isNotDir(f.getFileType()))
                .mapToLong(FileSystemItem::getSize).sum();
        skip += batch.size();

        boolean hasMore = batch.size() == batchSize;

        String eventTopic = hasMore ? EventTopics.MEDIA_FILE_TOPIC : EventTopics.MEDIA_HANDLER_TOPIC;
        publisher.publishEvent(new FileEventProducer.EventWrapper(
                eventTopic,
                event.userId(),
                new MediaUpdateEvent.DirectoryToAlbumMediaInitiated(
                        event.userId(),
                        event.fileId(),
                        bucket, objectName,
                        event.fileName(), event.uploadDate(),
                        event.searchable(),
                        size, skip,
                        event.parentMediaId()
                )
        ));

        FileSystemItem parent = fileService.findById(event.userId(), item.getParentId(), true);
        if (parent.getFileType() == FileType.GROUPER) {
            updateParentMediaLength(event.userId(), Collections.singletonList(parent), 1, true);
        }

        log.info("Created media for directory: {} with name: {}", item.getId(), item.getName());
    }

    private FileSystemItem findFirstImageOrVideo(String userId, String parentPath) {
        String quotedParentPath = Pattern.quote(parentPath);
        return mongoTemplate.findOne(new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.PATH).regex("^" + quotedParentPath)
                .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO)), FileSystemItem.class);
    }

    @Transactional
    public UpdateResult handleCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        var result = fileService.updateFileMetadataAsMedia(
                event.userId(),
                event.fileId(),
                event.mediaId(),
                FileType.detectFileTypeFromMediaType(event.mediaType()),
                event.thumbnail(),
                event.length(),
                event.width(),
                event.height()
        );
        fileLockService.releaseLockedFileItem(Set.of(event.fileId()));
        fileCacheService.invalidateFileCache(event.fileId());
        log.info("Completed file to media: {} with media id: {}", event.fileId(), event.mediaId());
        return result;
    }

    @Transactional
    public void handleInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        FileSystemItem item = fileService.findByMId(event.userId(), event.mediaId());
        if (item == null) {
            log.warn("Item not found, skipping update media thumbnail: {}", event.mediaId());
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
                    .skip(event.num()) // zero-based index
                    .limit(1);
            FileSystemItem numItem = mongoTemplate.findOne(query, FileSystemItem.class);
            if (numItem == null) {
                log.warn("Item with id: {} does not have child at num {}, skipping update media thumbnail", item.getId(), event.num());
                return;
            }
            objectName = numItem.getObjectName();
            bucket = numItem.getBucket();
        }

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_HANDLER_TOPIC,
                event.userId(),
                new MediaUpdateEvent.MediaThumbnailUpdated(
                        event.userId(),
                        item.getMId(),
                        event.mediaType(),
                        (double) event.num(),
                        bucket,
                        objectName)
        ));
        log.info("Initiated update media thumbnail: {} with media id: {}", item.getId(), item.getMId());
    }

    @Transactional
    public void handleUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        Query query = Query.query(Criteria.where(FileItemField.MEDIA_ID).is(event.mediaId()));
        Update update = new Update()
                .set(FileItemField.THUMBNAIL, event.newThumbnail());
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        log.info("Updated media thumbnail for media id: {}", event.mediaId());
    }

    @Transactional
    public void handleDeleteFile(MediaUpdateEvent.FileDeleted event) {
        FileSystemItem fileItem = fileService.findById(event.userId(), event.fileId(), true);
        if (fileItem == null) {
            log.warn("File not found with id: {}, skipping delete", event.fileId());
            return;
        }

        boolean isMedia = fileItem.getMId() != null && fileItem.getMId() > 0;
        if (isMedia && fileItem.getFileType() == FileType.ALBUM) {
            FileSystemItem parent = fileService.findById(event.userId(), fileItem.getParentId(), true);
            if (parent != null && parent.getFileType() == FileType.GROUPER) {
                updateParentMediaLength(event.userId(), Collections.singletonList(parent), -1, false);
            }
        }

        deleteFile(event.userId(), fileItem);
        log.info("Deleted file: {} with name: {}", fileItem.getId(), fileItem.getName());
    }

    @Transactional
    protected void deleteFile(String userId, FileSystemItem fileItem) {
        boolean hasMore = !FileType.isNotDir(fileItem.getFileType());

        Set<String> parentIds = fileService.getCommonIds(fileItem.getPath());
        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> parents = fileService.getItemInIds(parentIds, true, criteria, f -> f.getFileType() == FileType.ALBUM);

        final int batchSize = 500;
        while (hasMore) { // delete items in the target fileItem if the fileItem is a directory
            String parentPath = Pattern.quote(fileService.getPathForFileItem(fileItem.getPath(), fileItem.getId()));
            List<FileSystemItem> batch = mongoTemplate.find(Query.query(Criteria
                    .where(FileItemField.PATH).regex("^" + parentPath)).limit(batchSize), FileSystemItem.class);

            hasMore = batch.size() == batchSize;

            List<String> ids = new ArrayList<>();
            int fileCount = 0;
            Map<String, List<String>> toDelete = new HashMap<>();
            for (FileSystemItem item : batch) {
                ids.add(item.getId());
                if (FileType.isNotDir(item.getFileType())) {
                    fileCount++;
                    toDelete.computeIfAbsent(item.getBucket(), _ -> new ArrayList<>()).add(item.getObjectName());
                    if (item.getThumbnail() != null)
                        toDelete.computeIfAbsent(ContentMetaData.THUMBNAIL_BUCKET, _ -> new ArrayList<>()).add(item.getThumbnail());
                }
            }
            for (Map.Entry<String, List<String>> entry : toDelete.entrySet()) {
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_HANDLER_TOPIC,
                        userId,
                        new MediaUpdateEvent.ObjectDeleted(entry.getKey(), entry.getValue())
                ));
            }
            updateParentMediaLength(userId, parents, -fileCount, true);
            mongoTemplate.remove(new Query(Criteria.where("id").in(ids)), FileSystemItem.class);
            fileCacheService.invalidateFileCache(ids);
            log.info("Deleted {} items for folder {} ", ids.size(), fileItem.getId());
        }

        mongoTemplate.remove(new Query(Criteria.where("id").is(fileItem.getId())), FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileItem.getId());
        if (fileItem.getBucket() != null && fileItem.getObjectName() != null)
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_HANDLER_TOPIC,
                    userId,
                    new MediaUpdateEvent.ObjectDeleted(fileItem.getBucket(), Collections.singletonList(fileItem.getObjectName()))
            ));
        if (fileItem.getThumbnail() != null)
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_HANDLER_AND_BACKUP_TOPIC,
                    userId,
                    new MediaUpdateEvent.ThumbnailDeleted(fileItem.getThumbnail())
            ));

        if (FileType.isNotDir(fileItem.getFileType())) {
            updateParentMediaLength(userId, parents, -1, true);
        }
    }

    private void updateParentMediaLength(String userId, List<FileSystemItem> parents, int lengthDelta, boolean sendEvent) {
        List<String> parentIds = new ArrayList<>(parents.size());
        List<Long> parentMediaIds = new ArrayList<>(parents.size());
        for (FileSystemItem parent : parents) {
            parentIds.add(parent.getId());
            if (parent.getMId() != null)
                parentMediaIds.add(parent.getMId());
        }

        Query query = new Query(Criteria.where("id").in(parentIds));
        Update update = new Update().inc(FileItemField.LENGTH, lengthDelta);
        if (lengthDelta < 0)
            query.addCriteria(Criteria.where(FileItemField.LENGTH).gt(0));
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);

        if (!parentMediaIds.isEmpty() && sendEvent)
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_HANDLER_TOPIC,
                    userId,
                    new MediaUpdateEvent.MediaFileLengthUpdate(userId, parentMediaIds, lengthDelta)
            ));
    }


    @Transactional
    public void handleMoveDirectory(MediaUpdateEvent.DirectoryMoved event) {
        FileSystemItem item = fileService.findById(event.userId(), event.fileId(), true);
        if (item == null) {
            log.warn("File not found with id: {}, skipping move directory", event.fileId());
            return;
        }
        if (FileType.isNotDir(item.getFileType())) {
            log.warn("File is not a directory. Moving single file is handled at initiation already. Skipping... {}", event.fileId());
            return;
        }
        FileSystemItem newParent = fileService.findById(event.userId(), event.newParentId(), true);
        if (newParent == null) {
            log.warn("Parent not found with id: {}, skipping move directory", event.newParentId());
            return;
        }
        if (FileType.isNotDir(newParent.getFileType())) {
            log.warn("Parent is not a directory. Skipping... {}", event.newParentId());
            return;
        }

        FileType oldFileType = FileType.valueOf(event.fileType());
        if (oldFileType == FileType.ALBUM || oldFileType == FileType.GROUPER) {
            if (newParent.getFileType() == FileType.GROUPER) {
                updateParentMediaLength(event.userId(), Collections.singletonList(newParent), 1, false);
            }
            var oldParent = fileService.findById(event.userId(), event.oldParentId(), true);
            if (oldParent != null && oldParent.getFileType() == FileType.GROUPER) {
                updateParentMediaLength(event.userId(), Collections.singletonList(oldParent), -1, false);
            }
        }

        // needing oldPath since item or source dir path is already updated to reflect changes
        // item is the source directory, we need to get all children and update their paths
        String childrenIdPrefix = event.oldIdPath() + item.getId() + "/"; // all children in the directory
        String newIdPrefix = newParent.getPath() + newParent.getId() + "/";

        String anchoredRegex = "^" + Pattern.quote(childrenIdPrefix);
        Query query = new Query(Criteria.where(FileItemField.PATH).regex(anchoredRegex)); // find children
        AggregationUpdate update = AggregationUpdate.update()
                .set(FileItemField.PATH)
                .toValue(StringOperators.ReplaceOne.valueOf(FileItemField.PATH)
                        .find(event.oldIdPath()) // find old path prefix and replace with new path prefix
                        .replacement(newIdPrefix));
        mongoTemplate.updateMulti(query, update, FileSystemItem.class);

        Set<String> commonIds = fileService.getCommonIds(event.oldIdPath() + item.getId() + newParent.getPath() + newParent.getId());
        fileLockService.releaseLockedFileItem(commonIds);
        log.info("Moved directory: {} to parent: {}", item.getId(), newParent.getId());
    }
}
