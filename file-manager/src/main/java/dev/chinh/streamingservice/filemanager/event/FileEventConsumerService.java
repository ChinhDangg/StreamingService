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
import dev.chinh.streamingservice.filemanager.repository.FileRepository;
import dev.chinh.streamingservice.filemanager.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileEventConsumerService {

    private final FileRepository fileRepository;

    private final FileService fileService;
    private final FileTrashService fileTrashService;
    private final FileUtilService fileUtilService;
    private final DirectoryCacheService directoryCacheService;
    private final FileLockService fileLockService;

    private final ApplicationEventPublisher publisher;

    @Transactional
    public void handleCreateFile(MediaUpdateEvent.FileCreated event) {
        String rootId = fileUtilService.getROOT_FOLDER_ID();
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
        var savedFileItem = fileRepository.insert(fileItem);

        if (event.isLast()) {
            directoryCacheService.removeAllDirectoriesUserUsing(event.userId());
        }

        Set<String> parentIds = fileUtilService.getCommonIds(savedFileItem.getPath());
        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> parents = fileUtilService.getItemInIds(parentIds, true, criteria, f -> f.getFileType() == FileType.ALBUM);
        updateParentMediaLength(event.userId(), parents, 1, true);

        if (event.addAsVideo()) {
            fileService.addFileAsVideoMedia(String.valueOf(savedFileItem.getUserId()), savedFileItem.getId(), event.nameUpdateListAsJson());
        }

        log.info("Created file: {} with name: {}", savedFileItem.getId(), savedFileItem.getName());
    }



    @Transactional
    public void handleNestedDirectoryToGrouperMedia(MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated event) {
        FileSystemItem item = fileUtilService.findById(event.userId(), event.fileId(), true);
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
            FileSystemItem first = findFirstImageOrVideo(event.userId(), fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
            bucket = first.getBucket();
            objectName = first.getObjectName();
        }

        Query query = Query.query(Criteria.where(FileItemField.PARENT_ID).is(item.getId()))
                .with(Sort.by(Sort.Direction.ASC, FileItemField.NAME))
                .limit(batchSize)
                .skip(skip);
        List<FileSystemItem> directChildren = fileRepository.find(query);
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
        FileSystemItem item = fileUtilService.findById(event.userId(), event.fileId(), true);
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
            FileSystemItem first = findFirstImageOrVideo(event.userId(), fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
            if (first != null) {
                bucket = first.getBucket();
                objectName = first.getObjectName();
            }
        }

        String parentPath = Pattern.quote(fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
        Query query = Query.query(Criteria
                        .where(FileItemField.PATH).regex("^" + parentPath))
                .limit(batchSize)
                .skip(skip);
        query.fields().include("id", FileItemField.SIZE, FileItemField.FILE_TYPE);
        List<FileSystemItem> batch = fileRepository.find(query);

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

        FileSystemItem parent = fileUtilService.findById(event.userId(), item.getParentId(), true);
        if (parent.getFileType() == FileType.GROUPER) {
            updateParentMediaLength(event.userId(), Collections.singletonList(parent), 1, true);
        }

        log.info("Created media for directory: {} with name: {}", item.getId(), item.getName());
    }

    private FileSystemItem findFirstImageOrVideo(String userId, String parentPath) {
        String quotedParentPath = Pattern.quote(parentPath);
        return fileRepository.findOne(new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.PATH).regex("^" + quotedParentPath)
                .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO)
        ));
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
        log.info("Completed file to media: {} with media id: {}", event.fileId(), event.mediaId());
        return result;
    }

    @Transactional
    public void handleInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        FileSystemItem item = fileUtilService.findByMId(event.userId(), event.mediaId());
        if (item == null) {
            log.warn("Item not found, skipping update media thumbnail: {}", event.mediaId());
            return;
        }
        String objectName = item.getObjectName();
        String bucket = item.getBucket();
        if (!FileType.isNotDir(item.getFileType())) {
            String parentPath = Pattern.quote(fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
            Query query = Query.query(Criteria
                            .where(FileItemField.PATH).regex("^" + parentPath)
                            .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO))
                    .with(Sort.by(Sort.Direction.ASC, FileItemField.NAME))
                    .skip(event.num()) // zero-based index
                    .limit(1);
            FileSystemItem numItem = fileRepository.findOne(query);
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
        fileRepository.findAndModify(query, FindAndModifyOptions.options(), update);
        log.info("Updated media thumbnail for media id: {}", event.mediaId());
    }

    @Transactional
    public void handleDeleteFile(MediaUpdateEvent.FileDeleted event) {
        FileSystemItem fileItem = fileUtilService.findById(event.userId(), event.fileId(), true);
        if (fileItem == null) {
            log.warn("File not found with id: {}, skipping delete", event.fileId());
            return;
        }

        boolean isMedia = fileItem.getMId() != null && fileItem.getMId() > 0;
        if (isMedia && fileItem.getFileType() == FileType.ALBUM) {
            FileSystemItem parent = fileUtilService.findById(event.userId(), fileItem.getParentId(), true);
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

        Set<String> parentIds = fileUtilService.getCommonIds(fileItem.getPath());
        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> parents = fileUtilService.getItemInIds(parentIds, true, criteria, f -> f.getFileType() == FileType.ALBUM);

        final int batchSize = 500;
        while (hasMore) { // delete items in the target fileItem if the fileItem is a directory
            String parentPath = Pattern.quote(fileUtilService.getPathForFileItem(fileItem.getPath(), fileItem.getId()));
            List<FileSystemItem> batch = fileRepository.find(Query.query(Criteria
                    .where(FileItemField.PATH).regex("^" + parentPath)).limit(batchSize));

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
            fileRepository.remove(ids);
            log.info("Deleted {} items for folder {} ", ids.size(), fileItem.getId());
        }

        fileRepository.remove(fileItem.getId());
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
        fileRepository.updateMulti(parentIds, query, update);

        if (!parentMediaIds.isEmpty() && sendEvent)
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_HANDLER_TOPIC,
                    userId,
                    new MediaUpdateEvent.MediaFileLengthUpdate(userId, parentMediaIds, lengthDelta)
            ));
    }

    @Transactional
    public void handleMoveToTrash(MediaUpdateEvent.FilesMovedToTrash event) {
        List<FileSystemItem> items = fileUtilService.getItemInIds(event.fileIds(), true, null, null);
        if (items.isEmpty()) {
            log.warn("No items found with ids: {}, skipping move to trash", event.fileIds());
            return;
        }
        List<String> fileItemIds = new ArrayList<>();
        List<String> dirItemIds = new ArrayList<>();
        Map<String, FileSystemItem> fileItemMap = new HashMap<>();
        for (FileSystemItem item : items) {
            if (FileType.isNotDir(item.getFileType())) {
                fileItemIds.add(item.getId());
            } else {
                dirItemIds.add(item.getId());
            }
            fileItemMap.put(item.getId(), item);
        }

        UnaryOperator<AggregationUpdate> nameUpdate = update -> update
                .set(FileItemField.NAME).toValueOf(
                        StringOperators.Concat.valueOf(FileItemField.NAME)
                                .concat(FileTrashService.TRASH_START_POSTFIX)
                                .concatValueOf(ConvertOperators.ToString.toString("$_id"))
                )
                .set(FileItemField.OLD_PARENT_ID).toValueOf(FileItemField.PARENT_ID);

        Optional<Map<String, String>> trashPathToNameHelper = Optional.of(Map.of(FileTrashService.TRASH_PATH_NAME, fileUtilService.addUserIdToPathEnd(event.userId(), FileTrashService.TRASH_NAME)));

        if (!fileItemIds.isEmpty()) {
            Map<String, String> fileIdToOldPathMap = new HashMap<>();
            fileItemIds.forEach(id -> fileIdToOldPathMap.put(
                    id,
                    fileUtilService.getFullPathInName(fileItemMap.get(id).getPath() + fileItemMap.get(id).getId(), true, trashPathToNameHelper)));

            var movedFileIds = moveFiles(event.userId(), fileItemIds, fileTrashService.getTRASH_FOLDER_ID(), nameUpdate);

            var movedItem = fileUtilService.getItemInIds(movedFileIds, true, null, null);
            List<MediaUpdateEvent.FileMoved> filesMovedEvent = movedItem.stream().map(i ->
                    new MediaUpdateEvent.FileMoved(
                            i.getId(),
                            fileTrashService.getTRASH_FOLDER_ID(),
                            fileIdToOldPathMap.get(i.getId()),
                            fileUtilService.getFullPathInName(i.getPath() + i.getId(), true, trashPathToNameHelper)
                    )
            ).toList();

//            publisher.publishEvent(new FileEventProducer.EventWrapper(
//                    EventTopics.MEDIA_BACKUP_TOPIC,
//                    event.userId(),
//                    new MediaUpdateEvent.FilesMoved(
//                            event.userId(),
//                            filesMovedEvent
//                    )
//            ));
        }
        if (!dirItemIds.isEmpty()) {
            for (String dirId : dirItemIds) {
                var currentDir = fileItemMap.get(dirId);
                var oldDirPathName = fileUtilService.addUserIdToPathStart(event.userId(), fileUtilService.getFullPathInName(currentDir.getPath() + currentDir.getId(), true, trashPathToNameHelper));

                AggregationUpdate update = AggregationUpdate.update();
                update = nameUpdate.apply(update);
                update.set(FileItemField.PARENT_ID).toValue(fileTrashService.getTRASH_FOLDER_ID())
                        .set(FileItemField.PATH).toValue(fileTrashService.getTrashFileSystemItem().getPath());

                var updatedMovingDirOnly = fileRepository.findAndModify(
                        new Query(Criteria
                                .where("id").is(currentDir.getId())
                                .and(FileItemField.USER_ID).is(currentDir.getUserId())),
                        FindAndModifyOptions.options().returnNew(true),
                        update
                );
                String newDirPathName = fileUtilService.getFullPathInName(updatedMovingDirOnly.getPath() + updatedMovingDirOnly.getId(), true, trashPathToNameHelper);

                System.out.println("Checking dir path name: ");
                System.out.println(oldDirPathName);
                System.out.println(newDirPathName);

                var dirMoveEvent = new MediaUpdateEvent.DirectoryMoved(
                        event.userId(),
                        currentDir.getId(),
                        fileTrashService.getTRASH_FOLDER_ID(),
                        currentDir.getParentId(),
                        currentDir.getPath(),
                        oldDirPathName,
                        FileTrashService.TRASH_PATH + oldDirPathName + FileTrashService.TRASH_START_POSTFIX + currentDir.getId(),
                        currentDir.getFileType().name()
                );
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        event.userId(),
                        dirMoveEvent
                ));
            }
        }
    }

    @Transactional
    public void handleMoveOutOfTrash(MediaUpdateEvent.FilesMovedOutOfTrash event) {
        List<FileSystemItem> items = fileUtilService.getItemInIds(event.fileIds(), true, null, null);
        if (items.isEmpty()) {
            log.warn("No items found with ids: {}, skipping moving out of trash", event.fileIds());
            return;
        }
        List<String> fileItemIds = new ArrayList<>();
        List<String> dirItemIds = new ArrayList<>();
        Map<String, FileSystemItem> fileItemMap = new HashMap<>();
        for (FileSystemItem item : items) {
            if (FileType.isNotDir(item.getFileType())) {
                fileItemIds.add(item.getId());
            } else {
                dirItemIds.add(item.getId());
            }
            fileItemMap.put(item.getId(), item);
        }

        UnaryOperator<AggregationUpdate> undoNameUpdate = update -> update
                .set(FileItemField.NAME).toValueOf(
                        StringOperators.valueOf(FileItemField.NAME)
                                .replaceAll(
                                        StringOperators.Concat.stringValue(FileTrashService.TRASH_START_POSTFIX)
                                                .concatValueOf(ConvertOperators.ToString.toString("$_id")),
                                        ""
                                )
                )
                .unset(FileItemField.OLD_PARENT_ID);

        Optional<Map<String, String>> trashPathToNameHelper = Optional.of(Map.of(FileTrashService.TRASH_PATH_NAME, fileUtilService.addUserIdToPathEnd(event.userId(), FileTrashService.TRASH_NAME)));

        if (!fileItemIds.isEmpty()) {
            Map<String, String> fileIdToOldPathMap = new HashMap<>();
            Map<String, List<String>> sameOldParentToIdList = new HashMap<>();
            Map<String, String> fileIdToOldParentId = new HashMap<>();
            for (String id : fileItemIds) {
                FileSystemItem currentFile = fileItemMap.get(id);
                fileIdToOldPathMap.put(id, fileUtilService.getFullPathInName(currentFile.getPath() + currentFile.getId(), true, trashPathToNameHelper));
                var oldParentId = fileItemMap.get(id).getOldParentId();
                sameOldParentToIdList.computeIfAbsent(oldParentId, k -> new ArrayList<>())
                        .add(id);
                fileIdToOldParentId.put(id, oldParentId);
            }

            List<String> movedFileIds = new ArrayList<>();
            for (String oldParentId : sameOldParentToIdList.keySet()) {
                movedFileIds.addAll(moveFiles(event.userId(), sameOldParentToIdList.get(oldParentId), oldParentId, undoNameUpdate));
            }

            var movedItem = fileUtilService.getItemInIds(movedFileIds, true, null, null);
            List<MediaUpdateEvent.FileMoved> filesMovedEvent = movedItem.stream().map(i ->
                    new MediaUpdateEvent.FileMoved(
                            i.getId(),
                            fileIdToOldParentId.get(i.getId()), // using old parent id to move back
                            fileIdToOldPathMap.get(i.getId()),  // old path stored before moving (which update the metadata already)
                            fileUtilService.getFullPathInName(i.getPath() + i.getName(), true, trashPathToNameHelper)
                    )
            ).toList();

            System.out.println("Checking file path name for backup: ");
            System.out.println(filesMovedEvent);

//            publisher.publishEvent(new FileEventProducer.EventWrapper(
//                    EventTopics.MEDIA_BACKUP_TOPIC,
//                    event.userId(),
//                    new MediaUpdateEvent.FilesMoved(
//                            event.userId(),
//                            filesMovedEvent
//                    )
//            ));
        }
        if (!dirItemIds.isEmpty()) {
            for (String dirId : dirItemIds) {
                var currentDir = fileItemMap.get(dirId);
                var oldDirPathName = fileUtilService.getFullPathInName(currentDir.getPath() + currentDir.getId(), true, trashPathToNameHelper);
                FileSystemItem oldParentItem = currentDir.getOldParentId().equals(fileUtilService.getROOT_FOLDER_ID())
                        ? fileUtilService.getRootDirectoryItem()
                        : fileUtilService.findById(event.userId(), currentDir.getOldParentId(), true);

                AggregationUpdate update = AggregationUpdate.update()
                        .set(FileItemField.PARENT_ID).toValue(oldParentItem.getId())
                        .set(FileItemField.PATH).toValue(oldParentItem.getPath() + oldParentItem.getId() + "/");
                update = undoNameUpdate.apply(update);

                var updatedMovingDirOnly = fileRepository.findAndModify(
                        new Query(Criteria
                                .where("id").is(currentDir.getId())
                                .and(FileItemField.USER_ID).is(currentDir.getUserId())),
                        FindAndModifyOptions.options().returnNew(true),
                        update
                );
                var newDirPathName = fileUtilService.addUserIdToPathStart(event.userId(), fileUtilService.getFullPathInName(updatedMovingDirOnly.getPath() + updatedMovingDirOnly.getId(), true, trashPathToNameHelper));

                System.out.println("Checking dir path name: ");
                System.out.println(oldDirPathName);
                System.out.println(newDirPathName);

                var dirMoveEvent = new MediaUpdateEvent.DirectoryMoved(
                        event.userId(),
                        currentDir.getId(),
                        currentDir.getOldParentId(), // using old parent id to move back
                        currentDir.getParentId(),
                        currentDir.getPath(),
                        oldDirPathName,
                        newDirPathName,
                        currentDir.getFileType().name()
                );
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        event.userId(),
                        dirMoveEvent
                ));
            }
        }
    }

    @Transactional
    public void handleMoveDirectory(MediaUpdateEvent.DirectoryMoved event) {
        FileSystemItem item = fileUtilService.findById(event.userId(), event.fileId(), true);
        if (item == null) {
            log.warn("File not found with id: {}, skipping move directory", event.fileId());
            return;
        }
        if (FileType.isNotDir(item.getFileType())) {
            log.warn("File is not a directory. Moving single file is handled at initiation already. Skipping... {}", event.fileId());
            return;
        }
        FileSystemItem newParent = event.newParentId().equals(fileTrashService.getTRASH_FOLDER_ID())
                ? fileTrashService.getTrashFileSystemItem()
                : fileUtilService.findById(event.userId(), event.newParentId(), true);
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
            var oldParent = fileUtilService.findById(event.userId(), event.oldParentId(), true);
            if (oldParent != null && oldParent.getFileType() == FileType.GROUPER) {
                updateParentMediaLength(event.userId(), Collections.singletonList(oldParent), -1, false);
            }
        }

        final int batchSize = 500;

        // needing oldPath since item or source dir path is already updated to reflect changes
        // item is the source directory, we need to get all children and update their paths
        String childrenIdPrefix = event.oldIdPath() + item.getId() + "/"; // all children in the directory
        String newIdPrefix = newParent.getPath() + newParent.getId() + "/";

        String anchoredRegex = "^" + Pattern.quote(childrenIdPrefix);
        Query idQuery = new Query(Criteria.where(FileItemField.PATH).regex(anchoredRegex)).limit(batchSize); // find children
        idQuery.fields().include(FileItemField.ID);
        List<FileSystemItem> children = fileRepository.find(idQuery);

        if (!children.isEmpty()) {
            List<String> targetIds = children.stream().map(FileSystemItem::getId).toList();

            Query updateQuery = new Query(Criteria.where("id").in(targetIds));
            AggregationUpdate update = AggregationUpdate.update()
                    .set(FileItemField.PATH)
                    .toValue(StringOperators.ReplaceOne.valueOf(FileItemField.PATH)
                            .find(event.oldIdPath()) // find old path prefix and replace with new path prefix
                            .replacement(newIdPrefix));
            fileRepository.updateMulti(targetIds, updateQuery, update);
        }

        if (children.size() == batchSize) {
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    event.userId(),
                    event
            ));
            return;
        }

        Set<String> commonIds = fileUtilService.getCommonIds(event.oldIdPath() + item.getId() + newParent.getPath() + newParent.getId());
        System.out.println("common ids: ");
        System.out.println(commonIds);
        fileLockService.releaseLockedFileItem(commonIds);
        log.info("Moved directory: {} to parent: {}", item.getId(), newParent.getId());
    }

    @Transactional
    public void handleMoveFiles(MediaUpdateEvent.FilesMoved event) {
        Map<String, List<String>> sameNewParentToIdList = new HashMap<>();
        for (var fileMoving : event.fileMoves()) {
            sameNewParentToIdList.computeIfAbsent(fileMoving.newParentId(), k -> new ArrayList<>())
                    .add(fileMoving.fileId());
        }
        for (String newParentId : sameNewParentToIdList.keySet())
            moveFiles(event.userId(), sameNewParentToIdList.get(newParentId), newParentId, null);
    }

    private List<String> moveFiles(String userId, List<String> fileIds, String newParentId, UnaryOperator<AggregationUpdate> additionalUpdate) {
        List<FileSystemItem> items = fileUtilService.getItemInIds(fileIds, true, null, null);
        if (items.isEmpty()) {
            log.warn("No items found with ids: {}, skipping move", fileIds);
            return new ArrayList<>();
        }
        FileSystemItem parent = fileUtilService.findById(userId, newParentId, true);
        if (parent == null) {
            log.warn("Parent not found with id: {}, skipping move", newParentId);
            return new ArrayList<>();
        }
        if (FileType.isNotDir(parent.getFileType())) {
            log.warn("Parent is not a directory. Skipping... {}", newParentId);
        }
        List<String> fileItemIds = items.stream()
                .filter(f -> FileType.isNotDir(f.getFileType()))
                .map(FileSystemItem::getId)
                .toList();

        if (fileItemIds.isEmpty()) {
            log.warn("No files found in the list. Skipping move");
            return new ArrayList<>();
        }

        Query updateQuery = new Query(Criteria.where("id").in(fileItemIds));
        AggregationUpdate update = AggregationUpdate.update();

        if (additionalUpdate != null) // apply additional update first to let the caller work with the original fields
            update = additionalUpdate.apply(update);

        update = update
                .set(FileItemField.PATH).toValue(parent.getPath() + parent.getId() + "/")
                .set(FileItemField.PARENT_ID).toValue(parent.getId());
        fileRepository.updateMulti(fileItemIds, updateQuery, update);
        return fileItemIds;
    }
}
