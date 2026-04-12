package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.data.FolderLocks;
import dev.chinh.streamingservice.filemanager.event.FileEventProducer;
import dev.chinh.streamingservice.filemanager.repository.FileSystemRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MongoTemplate mongoTemplate;
    private final MongoTemplate safeWriteMongoTemplate;
    private final ApplicationEventPublisher publisher;
    private final FileSystemRepository fileSystemRepository;

    private final ThumbnailService thumbnailService;
    private final DirectoryCacheService directoryCacheService;
    private final FileCacheService fileCacheService;


    private static final String mediaPath = ContentMetaData.MEDIA_BUCKET;
    private static String ROOT_PATH = null;
    private static String ROOT_FOLDER_ID = null;

    public record FileSearchResult(String parentId, String parentName, List<FileSystemItem> content, Pageable pageable, boolean hasNext) {}

    public Slice<FileSystemItem> findFilesInDirectory(String userId, String parentId, int page, SortBy sortBy, Sort.Direction sortOrder) {
        FileSystemItem parent = getFileSystemItem(userId, parentId, true);
        String regexPath = "^" + Pattern.quote(parent.getPath() + parent.getId() + "/");

        return fileSystemRepository.findByUserIdAndPathRegex(Long.parseLong(userId), regexPath, getPageable(page, sortBy, sortOrder));
    }

    public FileSearchResult findFilesAtRoot(String userId, int page, SortBy sortBy, Sort.Direction sortOrder) {
        Slice<FileSystemItem> items = fileSystemRepository.findByUserIdAndParentId(Long.parseLong(userId), getROOT_FOLDER_ID(), getPageable(page, sortBy, sortOrder));
        List<FileSystemItem> itemInRoot = getUpdatedThumbnailUrl(userId, items.getContent());

        return new FileSearchResult(getROOT_FOLDER_ID(), mediaPath, itemInRoot, items.getPageable(), items.hasNext());
    }

    public FileSearchResult findFilesInDirectory(String userId, boolean getFullPathInfo, String parentId, int page, SortBy sortBy, Sort.Direction sortOrder) {
        Slice<FileSystemItem> items = fileSystemRepository.findByUserIdAndParentId(Long.parseLong(userId), parentId, getPageable(page, sortBy, sortOrder));
        List<FileSystemItem> itemInDir = getUpdatedThumbnailUrl(userId, items.getContent());

        if (getFullPathInfo) {
            FileSystemItem parentCraft = new FileSystemItem();
            String pathInId;
            if (itemInDir.isEmpty()) {
                FileSystemItem parent = getFileSystemItem(userId, parentId, true);
                pathInId = parent.getPath() + parent.getId() + "/";
                parentCraft.setName(parent.getName());
            } else {
                pathInId = itemInDir.getFirst().getPath();
                parentCraft.setName("unknown");
            }
            parentCraft.setPath(pathInId);
            String pathInName = getFullPathInName(parentCraft, false);
            return new FileSearchResult(pathInId, pathInName, itemInDir, items.getPageable(), items.hasNext());
        }

        return new FileSearchResult(itemInDir.isEmpty() ? null : itemInDir.getFirst().getParentId(), null, itemInDir, items.getPageable(), items.hasNext());
    }

    private List<FileSystemItem> getUpdatedThumbnailUrl(String userId, List<FileSystemItem> source) {
        List<String> thumbnailName = thumbnailService.processThumbnail(userId, source);
        for (int i = 0; i < thumbnailName.size(); i++) {
            source.get(i).setThumbnail(thumbnailName.get(i));
        }
        return source;
    }

    private Pageable getPageable(int page, SortBy sortBy, Sort.Direction sortOrder) {
        final int pageSize = 25;

        Sort sort = Sort.by(sortOrder, sortBy.getField());
        if (sortBy == SortBy.RESOLUTION) {
            // Automatically add the width tie-breaker
            sort = sort.and(Sort.by(sortOrder, ContentMetaData.RESOLUTION + "." + ContentMetaData.WIDTH));
        }
        return PageRequest.of(page, pageSize, sort);
    }

    public FileSearchResult searchFileByName(String userId, String parentId, String fileName, boolean isRecursive, int page) {
        FileSystemItem parent = getFileSystemItem(userId, parentId, true);

        List<AggregationOperation> stages = new ArrayList<>();

        String indexName = "fileNameSearchIndex";
        Document searchDoc = new Document("$search", new Document("index", indexName)
                .append("wildcard", new Document("query", "*" + fileName + "*")
                        .append("path", FileItemField.NAME)
                        .append("allowAnalyzedField", true)
                )
        );
        stages.add(context -> searchDoc);

        if (isRecursive) {
            String pathPrefix = "^" + Pattern.quote(parent.getPath() + parent.getId() + "/");
            stages.add(Aggregation.match(Criteria.where(FileItemField.PATH).regex(pathPrefix)));
        } else {
            stages.add(Aggregation.match(Criteria.where(FileItemField.PARENT_ID).is(parent.getId())));
        }

        final int size = 25;
        long skipCount = (long) page * size;

        stages.add(Aggregation.skip(skipCount));

        stages.add(Aggregation.limit(size));

        Aggregation aggregation = Aggregation.newAggregation(stages);
        List<FileSystemItem> results = mongoTemplate.aggregate(aggregation, "fs_metadata", FileSystemItem.class).getMappedResults();
        getUpdatedThumbnailUrl(userId, results);
        return new FileSearchResult(null, null, results, null, results.size() == size);
    }


    public String initiateUploadRequest(String userId, String filePath) {
        var validatedFile = FileSystemValidator.isValidPath(filePath);
        if (validatedFile.errorMessage() != null) {
            throw new IllegalArgumentException(validatedFile.errorMessage());
        }
        String validatedPath = validatedFile.validatedPath();
        String[] pathParts = validatedPath.split("/");
        int partLength = pathParts.length;
        String parentId = getROOT_FOLDER_ID();
        for (int i = 0; i < partLength-1; i++) {
            String dirId = directoryCacheService.getCachedElseDbDirectoryId(parentId, pathParts[i], userId, true);
            if (dirId == null) {
                return addCacheMediaUploadRequest(validatedPath);
            }
            parentId = dirId;
        }

        boolean exists = itemWithNameExists(userId, parentId, pathParts[partLength-1]);
        if (exists) throw new IllegalArgumentException("File already exists: " + validatedPath);
        return addCacheMediaUploadRequest(validatedPath);
    }

    private String addCacheMediaUploadRequest(String objectName) {
        String sessionId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String redisKey = "upload::" + sessionId;

        redisStringTemplate.opsForValue().set(redisKey, objectName, Duration.ofHours(1));
        return sessionId;
    }


    // using codeStatus as file status:
    // -1 - processing to be added as media
    // -2 - marked as deleted
    // -3 - in-use being written into

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addFileAsVideoMedia(String userId, String fileId) {
        FileSystemItem item = getFileSystemItem(userId, fileId, true);
        if (item.getFileType() != FileType.VIDEO) {
            throw new IllegalArgumentException("File is not a video");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as video");
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        updateStatusCode(fileId, FileStatus.PROCESSING);
        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        userId,
                        fileId, MediaType.VIDEO,
                        item.getBucket(), item.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        true, false)
        ));
        return "Processing as video";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addDirectoryAsAlbumMedia(String userId, String fileId) {
        FileSystemItem item = getFileSystemItem(userId, fileId, false);
        if (item.getFileType() == FileType.ALBUM) {
            throw new IllegalArgumentException("Item is already an album");
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as media");
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        FolderLocks folderIsLocked = checkIfFileItemInLock(getCommonIds(item.getPath() + item.getId()), null);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }

        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> items = getItemInIds(getCommonIds(item.getPath()), criteria);
        if (!items.isEmpty()) {
            throw new IllegalArgumentException("Has parent as album - cannot have album in an album");
        }

        updateStatusCode(fileId, FileStatus.PROCESSING);
        FileSystemItem first = findFirstImageOrVideo(userId, getPathForFileItem(item.getPath(), item.getId()));

        if (!item.getParentId().equals(getROOT_FOLDER_ID())) {
            FileSystemItem parent = getFileSystemItem(userId, item.getParentId(), true);
            if (parent.getFileType() == FileType.GROUPER) {
                publisher.publishEvent(new FileEventProducer.EventWrapper(
                        EventTopics.MEDIA_UPLOAD_TOPIC,
                        new MediaUpdateEvent.FileToMediaInitiated(
                                userId,
                                fileId, MediaType.ALBUM,
                                first == null ? null : first.getBucket(), first == null ? null : first.getObjectName(),
                                item.getName(), item.getUploadDate(),
                                parent.getMId(), null,
                                false, true)
                ));
                return "Processing as album in grouper";
            }
        }

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        userId,
                        fileId, MediaType.ALBUM,
                        first == null ? null : first.getBucket(), first == null ? null : first.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        true, false)
        ));
        return "Processing as album";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addDirectoryAsGrouperMedia(String userId, String fileId) {
        FileSystemItem item = getFileSystemItem(userId, fileId, false);
        if (item.getFileType() == FileType.GROUPER) {
            throw new IllegalArgumentException("Item is already a grouper");
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as media");
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        FolderLocks folderIsLocked = checkIfFileItemInLock(getCommonIds(item.getPath() + item.getId()), null);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }

        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> items = getItemInIds(getCommonIds(item.getPath()), criteria);
        if (!items.isEmpty()) {
            throw new IllegalArgumentException("Has parent as album - cannot have grouper in an album");
        }

        updateStatusCode(fileId, FileStatus.PROCESSING);
        boolean anyDirectFile = mongoTemplate.exists(Query.query(Criteria
                        .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                        .and(FileItemField.PARENT_ID).is(fileId)
                        .and(FileItemField.FILE_TYPE).nin(FileType.DIR, FileType.ALBUM, FileType.GROUPER)),
                FileSystemItem.class);
        if (anyDirectFile) {
            throw new IllegalArgumentException("Contains direct files - can't be grouped - must include only direct directories");
        }
        FileSystemItem first = findFirstImageOrVideo(userId, getPathForFileItem(item.getPath(), item.getId()));

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        userId,
                        fileId, MediaType.GROUPER,
                        first == null ? null : first.getBucket(), first == null ? null : first.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        true, false)
        ));
        return "Processing as grouper";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public FileSystemItem createNewDirectory(String userId, String parentId, String newFolderName) {
        String error = FileSystemValidator.isValidName(newFolderName);
        if (error != null)
            throw new IllegalArgumentException(error);
        FileSystemItem parent = findById(userId, parentId, true);
        if (parent == null)
            throw new IllegalArgumentException("Parent folder not found: " + parentId);
        if (FileType.isNotDir(parent.getFileType()))
            throw new IllegalArgumentException("Parent folder is not a directory: " + parentId);
        if (itemWithNameExists(userId, parentId, newFolderName))
            throw new IllegalArgumentException("Folder already exists: " + newFolderName);
        FileSystemItem item = FileSystemItem.builder()
                .userId(Long.parseLong(userId))
                .parentId(parentId)
                .path(parent.getPath() + parentId + "/")
                .fileType(FileType.DIR)
                .name(newFolderName)
                .uploadDate(Instant.now())
                .build();

        var saved = mongoTemplate.insert(item);

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_BACKUP_TOPIC,
                new MediaUpdateEvent.DirectoryCreated(saved.getId(), addUserIdToPath(userId, getFullPathInName(saved, true)))
        ));

        return saved;
    }

    @Transactional
    public String renameFileItem(String userId, String fileId, String newName) {
        FileSystemItem item = getFileSystemItem(userId, fileId, true);
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        String error = FileSystemValidator.isValidName(newName);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (item.getName().equals(newName)) {
            return newName;
        }
        if (itemWithNameExists(userId, item.getParentId(), newName)) {
            throw new IllegalArgumentException("File already exists with name: " + newName);
        }
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set(FileItemField.NAME, newName);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileId);

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_BACKUP_TOPIC,
                new MediaUpdateEvent.FileRenamed(item.getId(), addUserIdToPath(userId, getFullPathInName(item, true)), newName)
        ));

        return newName;
    }

    @Transactional
    public void initiateDeleteFile(String userId, String fileId) {
        FileSystemItem item = getFileSystemItem(userId, fileId, false);
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            boolean anyChildMedia = anyChildMedia(userId, getPathForFileItem(item.getPath(), item.getId()));
            if (anyChildMedia) {
                throw new IllegalArgumentException("Directory is not empty - include media item");
            }
        }
        updateStatusCode(fileId, FileStatus.DELETING);
        if (item.getMId() != null && item.getMId() > 0) {
            throw new IllegalArgumentException("File is already marked as media - delete through media file item instead: " + item.getMId());
        }

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileDeleted(userId, item.getId(), addUserIdToPath(userId, getFullPathInName(item, true)), FileType.isNotDir(item.getFileType()), null)
        ));
    }

    @Transactional
    public void initiateDeleteMediaFile(String userId, long mediaId) {
        FileSystemItem item = findByMId(userId, mediaId);
        if (item == null) {
            throw new IllegalArgumentException("Media file not found: " + mediaId);
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            boolean anyChildMedia = anyChildMedia(userId, getPathForFileItem(item.getPath(), item.getId()));
            if (anyChildMedia) {
                throw new IllegalArgumentException("Media is not empty - include nested media item");
            }
        }
        updateStatusCode(item.getId(), FileStatus.DELETING);

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileDeleted(
                        userId, item.getId(), addUserIdToPath(userId, getFullPathInName(item, true)), FileType.isNotDir(item.getFileType()), item.getMId())
        ));
    }

    @Transactional
    public FileSystemItem initiateMoveFileItem(String userId, String fileId, String newParentId) {
        FileSystemItem item = getFileSystemItem(userId, fileId, false);
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (item.getParentId().equals(newParentId)) {
            throw new IllegalArgumentException("Cannot move item to same parent");
        }
        FileSystemItem newParent = findById(userId, newParentId, false);
        if (newParent == null) {
            throw new IllegalArgumentException("Parent folder not found: " + newParentId);
        }
        String statusCodeStr2 = FileSystemItem.getStatusCodeAsString(newParent.getStatusCode());
        if (statusCodeStr2 != null) {
            throw new IllegalArgumentException("Parent is busy: " + statusCodeStr2);
        }
        if (FileType.isNotDir(newParent.getFileType())) {
            throw new IllegalArgumentException("Parent is not a directory: " + newParentId);
        }
        if (item.getId().equals(newParentId)) {
            throw new IllegalArgumentException("Cannot move item to itself");
        }
        if (newParent.getPath().contains(item.getId())) {
            throw new IllegalArgumentException("Cannot move item to a child of itself");
        }
        Map<String, FileStatus> parentStatusMap = getCommonIds(newParent.getPath() + newParent.getId()).stream()
                .collect(Collectors.toMap(id -> id, _ -> FileStatus.BEING_MOVED_INTO));

        Set<String> commonIds = getCommonIds(item.getPath() + item.getId() + newParent.getPath() + newParent.getId());
        FolderLocks folderIsLocked = checkIfFileItemInLock(commonIds, parentStatusMap);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }
        if (itemWithNameExists(userId, newParentId, item.getName())) {
            throw new IllegalArgumentException("File already exists with name: " + item.getName());
        }

        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update()
                .set(FileItemField.PARENT_ID, newParentId)
                .set(FileItemField.PATH, newParent.getPath() + newParent.getId() + "/");

        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            lockFileItem(userId, commonIds, parentStatusMap);
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                    new MediaUpdateEvent.DirectoryMoved(
                            userId, fileId, newParentId, item.getPath(), addUserIdToPath(userId, getFullPathInName(item, true)), addUserIdToPath(userId, getFullPathInName(newParent, true))
                    )
            ));

            if (item.getFileType() == FileType.ALBUM && !item.getParentId().equals(getROOT_FOLDER_ID())) {
                FileSystemItem oldParent = findById(userId, item.getParentId(), true);
                if (oldParent.getFileType() == FileType.GROUPER) {
                    boolean newParentIsGrouper = newParent.getFileType() == FileType.GROUPER;
                    publisher.publishEvent(new FileEventProducer.EventWrapper(
                            EventTopics.MEDIA_UPLOAD_TOPIC,
                            new MediaUpdateEvent.GrouperItemMoved(userId, item.getMId(), newParentIsGrouper ? newParent.getMId() : null, item.getName())
                    ));
                    if (!newParentIsGrouper) {
                        update.unset(FileItemField.MEDIA_ID);
                        update.unset(FileItemField.RESOLUTION_INFO);
                        update.unset(FileItemField.LENGTH);
                        update.unset(FileItemField.THUMBNAIL);
                        update.set(FileItemField.FILE_TYPE, FileType.DIR);
                    }
                }
            }
        } else {
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_BACKUP_TOPIC,
                    new MediaUpdateEvent.FileMoved(
                            fileId, addUserIdToPath(userId, getFullPathInName(item, true)), addUserIdToPath(userId, getFullPathInName(newParent, true))
                    )
            ));
        }

        FileSystemItem moved = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), FileSystemItem.class);

        if (moved != null && (item.getFileType() == FileType.IMAGE || item.getThumbnail() != null)) {
            String thumbnailPath = ThumbnailService.getThumbnailPath(
                    ThumbnailService.getThumbnailParentPath(),
                    item.getMId() == null ? item.getId() : item.getMId().toString(),
                    item.getThumbnail() == null ? item.getObjectName() : item.getThumbnail()
            );
            moved.setThumbnail(thumbnailPath);
        }

        return moved;
    }

    public Set<String> getCommonIds(String idPath) {
        String[] idList = idPath.split("/");
        Set<String> commonIds = new HashSet<>(Arrays.asList(idList));
        commonIds.remove("");
        commonIds.remove(getROOT_FOLDER_ID());
        return commonIds;
    }

    private void lockFileItem(String userId, Set<String> fileIds, Map<String, FileStatus> statusMap) {
        if (fileIds.isEmpty()) return;
        // Use UNORDERED to process everything even if some IDs already exist
        BulkOperations bulkOps = safeWriteMongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, FolderLocks.class);

        for (String id : fileIds) {
            Query query = new Query(Criteria.where("id").is(id));
            Update update = new Update()
                    .setOnInsert(FileItemField.USER_ID, userId)
                    .setOnInsert(FileItemField.STATUS_CODE, statusMap.getOrDefault(id, FileStatus.PROCESSING));
            // use setOnInsert, if the ID exists, nothing happens.
            // If it doesn't exist, a new document is created with these values.
            bulkOps.upsert(query, update);
        }
        bulkOps.execute();
    }

    public void releaseLockedFileItem(Set<String> fileIds) {
        if (fileIds.isEmpty()) return;
        Query query = new Query(Criteria.where("id").in(fileIds));
        safeWriteMongoTemplate.remove(query, FolderLocks.class);
    }

    private FolderLocks checkIfFileItemInLock(Set<String> fileIds, Map<String, FileStatus> ignoredStatusMap) {
        if (fileIds.isEmpty()) return null;
        Criteria criteria = Criteria.where("id").in(fileIds);
        if (ignoredStatusMap != null && !ignoredStatusMap.isEmpty()) {
            List<Criteria> excludeCriteria = new ArrayList<>();
            ignoredStatusMap.forEach((id, status) -> {
                excludeCriteria.add(Criteria.where("id").is(id).and(FileItemField.STATUS_CODE).is(status));
            });
            // "nor" ensures that none of these specific ID+Status pairs are returned
            criteria.norOperator(excludeCriteria.toArray(new Criteria[0]));
        }
        return safeWriteMongoTemplate.findOne(new Query(criteria), FolderLocks.class);
    }


    private boolean itemWithNameExists(String userId, String parentId, String name) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name));
        return mongoTemplate.exists(query, FileSystemItem.class);
    }

    private boolean anyChildMedia(String userId, String parentPath) {
        String quotedParentPath = Pattern.quote(parentPath);
        return mongoTemplate.exists(
                new Query(Criteria
                        .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                        .and(FileItemField.PATH).regex("^" + quotedParentPath)
                        .and(FileItemField.MEDIA_ID).nin(null, 0)),
                FileSystemItem.class);
    }

    private FileSystemItem findFirstImageOrVideo(String userId, String parentPath) {
        String quotedParentPath = Pattern.quote(parentPath);
        return mongoTemplate.findOne(new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.PATH).regex("^" + quotedParentPath)
                .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO)), FileSystemItem.class);
    }

    private String addUserIdToPath(String userId, String path) {
        if (path.startsWith(userId + "/")) return path;
        if (path.isBlank()) return userId;
        return userId + "/" + path;
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public UpdateResult updateFileMetadataAsMedia(String userId, String fileId, long mediaId, FileType fileType, String thumbnailObject,
                                                  int length, Integer width, Integer height) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and("id").is(fileId)
        );
        Update update = new Update()
                .set(FileItemField.MEDIA_ID, mediaId)
                .set(FileItemField.FILE_TYPE, fileType)
                .set(FileItemField.LENGTH, length)
                .unset(FileItemField.STATUS_CODE);
        if (thumbnailObject != null)
            update.set(FileItemField.THUMBNAIL, thumbnailObject);
        if (width != null && height != null)
            update.set(FileItemField.RESOLUTION_INFO, new FileSystemItem.ResolutionInfo(width, height));
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    public String getFullPathInName(FileSystemItem item, boolean omitRoot) {
        String pathInId = item.getPath();
        List<String> pathIds = Arrays.stream(pathInId.split("/"))
                .filter(s -> !s.isEmpty() && (!omitRoot || !s.equals(getROOT_FOLDER_ID())))
                .toList();

        if (pathIds.isEmpty()) {
            if (omitRoot && item.getId().equals(getROOT_FOLDER_ID()))
                return "";
            return item.getName();
        }

        List<FileSystemItem> parents = getItemInIds(pathIds, null);

        Map<String, String> nameMap = parents.stream().collect(Collectors.toMap(FileSystemItem::getId, FileSystemItem::getName));

        return pathIds.stream()
                .map(id -> nameMap.getOrDefault(id, "Unknown"))
                .collect(Collectors.joining("/")) + "/" + item.getName();
    }

    /**
     *  need the returning path to start and end with "/"
     */
    public String getPathForFileItem(String parentPath, String currentPath) {
        String path = OSUtil.normalizePath(parentPath, currentPath + "/");
        if (path.startsWith(getROOT_PATH()))
            return path;
        return getROOT_PATH() + path;
    }


    private void updateStatusCode(String fileId, FileStatus status) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set(FileItemField.STATUS_CODE, status.getValue());
        safeWriteMongoTemplate.updateFirst(query, update, FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileId);
    }

    // since directories get set as in-use in caching for folder upload - reset all at startup for dangling status
    @EventListener(ApplicationReadyEvent.class)
    public void resetAllFileInUseStatus() {
        mongoTemplate.updateMulti(
                Query.query(Criteria
                        .where(FileItemField.STATUS_CODE).is(FileStatus.IN_USE.getValue())),
                new Update().unset(FileItemField.STATUS_CODE), FileSystemItem.class);
    }


    public FileSystemItem findByMId(String userId, long mId) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.MEDIA_ID).is(mId)
        );
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }

    private FileSystemItem getFileSystemItem(String userId, String id, boolean getCachedFirst) {
        FileSystemItem item = findById(userId, id, getCachedFirst);
        if (item == null)
            throw new IllegalArgumentException("File not found with id: " + id);
        return item;
    }

    public FileSystemItem findById(String userId, String id, boolean getCachedFirst) {
        if (id.equals(getROOT_FOLDER_ID()))
            return getRootDirectoryItem();
        return fileCacheService.getFileCacheElseFromDatabase(userId, id, getCachedFirst);
    }

    public List<FileSystemItem> getItemInIds(Collection<String> ids, Criteria criteria) {
        return fileCacheService.getCachedFilesElseFromDatabase(ids, criteria);
    }

    public FileSystemItem getRootDirectoryItem() {
        return fileCacheService.getFileCache(getROOT_FOLDER_ID(), (rootId) -> {
            Query query = new Query(Criteria
                    .where("id").is(rootId)
            );
            return mongoTemplate.findOne(query, FileSystemItem.class);
        });
    }

    public String getROOT_FOLDER_ID() {
        if (ROOT_FOLDER_ID != null) return ROOT_FOLDER_ID;
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(mediaPath)
                .and(FileItemField.PATH).is("/")
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );
        FileSystemItem item = mongoTemplate.findOne(query, FileSystemItem.class);

        if (item == null) throw new RuntimeException("Failed to find root folder");

        ROOT_FOLDER_ID = item.getId();
        return ROOT_FOLDER_ID;
    }

    private String getROOT_PATH() {
        if (ROOT_PATH != null) return ROOT_PATH;
        ROOT_PATH = "/" + getROOT_FOLDER_ID() + "/";
        return ROOT_PATH;
    }

    public void createRootFolder() {
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(mediaPath)
                .and(FileItemField.PATH).is("/")
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );

        Update update = new Update()
                .setOnInsert(FileItemField.NAME, mediaPath)
                .setOnInsert(FileItemField.PATH, "/")
                .setOnInsert(FileItemField.FILE_TYPE, FileType.DIR);

        UpdateResult result = mongoTemplate.upsert(query, update, FileSystemItem.class);
        if (!result.wasAcknowledged()) throw new RuntimeException("Failed to create root folder");
    }
}
