package dev.chinh.streamingservice.filemanager.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.config.ApplicationConfig;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.data.FolderLocks;
import dev.chinh.streamingservice.filemanager.event.MediaFileEventProducer;
import dev.chinh.streamingservice.filemanager.repository.FileSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final FileSystemRepository fileSystemRepository;
    private final MongoTemplate mongoTemplate;
    private final ThumbnailService thumbnailService;

    private final Cache<String, ApplicationConfig.EntryCached> directoryIdCache;

    private final MediaFileEventProducer producer;

    private static final String mediaPath = ContentMetaData.MEDIA_BUCKET;
    private static String ROOT_PATH = null;
    private static String ROOT_FOLDER_ID = null;

    public record FileSearchResult(String parentId, String parentName, List<FileSystemItem> content, Pageable pageable, boolean hasNext) {}

    public Slice<FileSystemItem> findFilesInDirectory(String parentId, int page, SortBy sortBy, Sort.Direction sortOrder) {
        return fileSystemRepository.findByParentId(parentId, getPageable(page, sortBy, sortOrder));
    }

    public FileSearchResult findFilesAtRoot(int page, SortBy sortBy, Sort.Direction sortOrder) {
        Slice<FileSystemItem> items = fileSystemRepository.findByParentId(getROOT_FOLDER_ID(), getPageable(page, sortBy, sortOrder));
        List<FileSystemItem> itemInRoot = items.getContent();
        List<String> thumbnailName = thumbnailService.processThumbnail(itemInRoot);
        for (int i = 0; i < thumbnailName.size(); i++) {
            itemInRoot.get(i).setThumbnail(thumbnailName.get(i));
        }

        return new FileSearchResult(getROOT_FOLDER_ID(), mediaPath, itemInRoot, items.getPageable(), items.hasNext());
    }

    public FileSearchResult findFilesInDirectory(boolean getFullPathInfo, String parentId, int page, SortBy sortBy, Sort.Direction sortOrder) {
        Slice<FileSystemItem> items = fileSystemRepository.findByParentId(parentId, getPageable(page, sortBy, sortOrder));
        List<FileSystemItem> itemInDir = items.getContent();
        List<String> thumbnailName = thumbnailService.processThumbnail(itemInDir);
        for (int i = 0; i < thumbnailName.size(); i++) {
            itemInDir.get(i).setThumbnail(thumbnailName.get(i));
        }

        if (getFullPathInfo) {
            FileSystemItem parentCraft = new FileSystemItem();
            String pathInId;
            if (itemInDir.isEmpty()) {
                FileSystemItem parent = getFileSystemItem(parentId);
                pathInId = parent.getPath() + parent.getId() + "/";
                parentCraft.setName(parent.getName());
            } else {
                pathInId = itemInDir.getFirst().getPath();
                parentCraft.setName("unknown");
            }
            parentCraft.setPath(pathInId);
            String pathInName = getFullPathInName(parentCraft);
            return new FileSearchResult(pathInId, pathInName, itemInDir, items.getPageable(), items.hasNext());
        }

        return new FileSearchResult(itemInDir.isEmpty() ? null : itemInDir.getFirst().getParentId(), null, itemInDir, items.getPageable(), items.hasNext());
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

    public String initiateUploadRequest(String filePath, String userId) {
        var validatedFile = FileSystemValidator.isValidPath(filePath);
        if (validatedFile.errorMessage() != null) {
            throw new IllegalArgumentException(validatedFile.errorMessage());
        }
        String validatedPath = validatedFile.validatedPath();
        String[] pathParts = validatedPath.split("/");
        int partLength = pathParts.length;
        String parentId = getROOT_FOLDER_ID();
        for (int i = 0; i < partLength-1; i++) {
            String dirId = getCachedElseDbDirectoryId(parentId, pathParts[i], userId);
            if (dirId == null) {
                return addCacheMediaUploadRequest(validatedPath);
            }
            parentId = dirId;
        }

        boolean exists = mongoTemplate.exists(new Query(Criteria
                        .where(FileItemField.PARENT_ID).is(parentId)
                        .and(FileItemField.NAME).is(pathParts[partLength-1])),
                FileSystemItem.class);
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
    public String addFileAsVideoMedia(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getFileType() != FileType.VIDEO) {
            throw new IllegalArgumentException("File is not a video");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            return "Item is already marked as video";
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            return statusCodeStr;
        }
        updateStatusCodeAndGet(fileId, FileStatus.PROCESSING);
        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        fileId, MediaType.VIDEO,
                        item.getBucket(), item.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        null, true)
        ));
        return "Processing as video";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String addDirectoryAsAlbumMedia(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getFileType() == FileType.ALBUM) {
            return "Item is already an album";
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            return "Item is already marked as media";
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            return statusCodeStr;
        }
        FolderLocks folderIsLocked = checkIfFileItemInLock(getCommonIds(item.getPath() + item.getId()));
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }
        updateStatusCodeAndGet(fileId, FileStatus.PROCESSING);
        String parentPath = Pattern.quote(getPathForFileItem(item.getPath(), item.getId()));
        FileSystemItem first = mongoTemplate.findOne(new Query(Criteria
                .where(FileItemField.PATH).regex("^" + parentPath)
                .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO)), FileSystemItem.class);

        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        fileId, MediaType.ALBUM,
                        first == null ? null : first.getBucket(), first == null ? null : first.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        null, true)
        ));
        return "Processing as album";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String addDirectoryAsGrouperMedia(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getFileType() == FileType.GROUPER) {
            return "Item is already a grouper";
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            return "Item is already marked as media";
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            return statusCodeStr;
        }
        FolderLocks folderIsLocked = checkIfFileItemInLock(getCommonIds(item.getPath() + item.getId()));
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }
        updateStatusCodeAndGet(fileId, FileStatus.PROCESSING);
        boolean anyDirectFile = mongoTemplate.exists(Query.query(Criteria
                        .where(FileItemField.PARENT_ID).is(fileId)
                        .and(FileItemField.FILE_TYPE).nin(FileType.DIR, FileType.ALBUM, FileType.GROUPER)),
                FileSystemItem.class);
        if (anyDirectFile) {
            return "Contains direct files - can't be grouped - must include only direct directories";
        }
        String parentPath = Pattern.quote(getPathForFileItem(item.getPath(), item.getId()));
        FileSystemItem first = mongoTemplate.findOne(new Query(Criteria
                .where(FileItemField.PATH).regex("^" + parentPath)
                .and(FileItemField.FILE_TYPE).in(FileType.IMAGE, FileType.VIDEO)), FileSystemItem.class);

        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_UPLOAD_TOPIC,
                new MediaUpdateEvent.FileToMediaInitiated(
                        fileId, MediaType.GROUPER,
                        first == null ? null : first.getBucket(), first == null ? null : first.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        null, null,
                        null, true)
        ));
        return "Processing as grouper";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public FileSystemItem createNewFolder(String parentId, String newFolderName) {
        String error = FileSystemValidator.isValidName(newFolderName);
        if (error != null)
            throw new IllegalArgumentException(error);
        FileSystemItem parent = findById(parentId);
        if (parent == null)
            throw new IllegalArgumentException("Parent folder not found: " + parentId);
        if (parent.getFileType() != FileType.DIR)
            throw new IllegalArgumentException("Parent folder is not a directory: " + parentId);
        if (itemWithNameExists(parentId, newFolderName))
            throw new IllegalArgumentException("Folder already exists: " + newFolderName);
        FileSystemItem item = FileSystemItem.builder()
                .parentId(parentId)
                .path(parent.getPath() + parentId + "/")
                .fileType(FileType.DIR)
                .name(newFolderName)
                .uploadDate(Instant.now())
                .build();
        return mongoTemplate.insert(item);
    }

    private String getOrCreateFolder(String name, String parentId, String currentPath, FileType fileType) {
        Query query = new Query(Criteria
                .where(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name)
                .and(FileItemField.FILE_TYPE).in(FileType.DIR, FileType.ALBUM)
        );

        // setOnInsert to create if not exists
        Update update = new Update()
                .setOnInsert(FileItemField.NAME, name)
                .setOnInsert(FileItemField.PARENT_ID, parentId)
                .setOnInsert(FileItemField.PATH, currentPath)
                .setOnInsert(FileItemField.FILE_TYPE, fileType)
                .setOnInsert(FileItemField.STATUS_CODE, FileStatus.IN_USE.getValue())
                .setOnInsert(FileItemField.UPLOAD_DATE, LocalDateTime.now());

        // upsert to create and return in one operation - atomic
        FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);

        FileSystemItem dir = mongoTemplate.findAndModify(query, update, options, FileSystemItem.class);

        if (dir == null) throw new RuntimeException("Failed to create folder");

        return dir.getId();
    }

    public String renameFileItem(String fileId, String newName) {
        FileSystemItem item = getFileSystemItem(fileId);
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
        if (itemWithNameExists(item.getParentId(), newName)) {
            throw new IllegalArgumentException("File already exists with name: " + newName);
        }
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set(FileItemField.NAME, newName);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        return newName;
    }

    public void initiateDeleteFile(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            String parentPath = Pattern.quote(getPathForFileItem(item.getPath(), item.getId()));
            boolean anyChildMedia = mongoTemplate.exists(
                    new Query(Criteria
                            .where(FileItemField.PATH).regex("^" + parentPath)
                            .and(FileItemField.MEDIA_ID).nin(null, 0)),
                    FileSystemItem.class);
            if (anyChildMedia) {
                throw new IllegalArgumentException("Directory is not empty - include media item");
            }
        }
        updateStatusCodeAndGet(fileId, FileStatus.DELETING);
        if (item.getMId() != null && item.getMId() > 0) {
            throw new IllegalArgumentException("File is already marked as media - delete through media file item instead: " + item.getMId());
        }

        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileDeleted(item.getId(), getFullPathInName(item), FileType.isNotDir(item.getFileType()), null)
        ));
    }

    public void initiateDeleteMediaFile(long mediaId) {
        FileSystemItem item = findByMId(mediaId);
        if (item == null) {
            throw new IllegalArgumentException("Media file not found: " + mediaId);
        }
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            String parentPath = Pattern.quote(getPathForFileItem(item.getPath(), item.getId()));
            boolean anyChildMedia = mongoTemplate.exists(
                    new Query(Criteria
                            .where(FileItemField.PATH).regex("^" + parentPath)
                            .and(FileItemField.MEDIA_ID).nin(null, 0)),
                    FileSystemItem.class);
            if (anyChildMedia) {
                throw new IllegalArgumentException("Media is not empty - include nested media item");
            }
        }
        updateStatusCodeAndGet(item.getId(), FileStatus.DELETING);

        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileDeleted(
                        item.getId(), getFullPathInName(item), FileType.isNotDir(item.getFileType()), item.getMId())
        ));
    }

    @Transactional
    public FileSystemItem initiateMoveFileItem(String fileId, String newParentId, String userId) {
        FileSystemItem item = getFileSystemItem(fileId);
        String statusCodeStr = FileSystemItem.getStatusCodeAsString(item.getStatusCode());
        if (statusCodeStr != null) {
            throw new IllegalArgumentException(statusCodeStr);
        }
        if (item.getParentId().equals(newParentId)) {
            throw new IllegalArgumentException("Cannot move item to same parent");
        }
        FileSystemItem parent = findById(newParentId);
        if (parent == null) {
            throw new IllegalArgumentException("Parent folder not found: " + newParentId);
        }
        String statusCodeStr2 = FileSystemItem.getStatusCodeAsString(parent.getStatusCode());
        if (statusCodeStr2 != null) {
            throw new IllegalArgumentException("Parent is busy: " + statusCodeStr2);
        }
        if (FileType.isNotDir(parent.getFileType())) {
            throw new IllegalArgumentException("Parent is not a directory: " + newParentId);
        }
        if (item.getId().equals(newParentId)) {
            throw new IllegalArgumentException("Cannot move item to itself");
        }
        if (parent.getPath().contains(item.getId())) {
            throw new IllegalArgumentException("Cannot move item to a child of itself");
        }
        Set<String> commonIds = getCommonIds(item.getPath() + item.getId() + parent.getPath() + parent.getId());
        FolderLocks folderIsLocked = checkIfFileItemInLock(commonIds);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException("File is locked: " + folderIsLocked.getId());
        }

        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update()
                .set(FileItemField.PARENT_ID, newParentId)
                .set(FileItemField.PATH, parent.getPath() + parent.getId() + "/");
        FileSystemItem moved = mongoTemplate.findAndModify(query, update, FileSystemItem.class);

        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            lockFileItem(userId, commonIds);
            producer.publishEventListener(new MediaFileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    new MediaUpdateEvent.DirectoryMoved(fileId, newParentId, item.getPath())
            ));
        }

        if (moved != null) {
            String thumbnailPath = ThumbnailService.getThumbnailPath(
                    item.getMId() == null ? item.getId() : item.getMId().toString(),
                    item.getThumbnail() == null ? item.getObjectName() : item.getThumbnail()
            );
            moved.setThumbnail(thumbnailPath);
        }

        return moved;
    }

    @Transactional
    public void moveDirectory(String fileId, String newParentId, String oldPath) {
        FileSystemItem item = findById(fileId);
        if (item == null) {
            System.err.println("File not found. Skipping...");
            return;
        }
        if (FileType.isNotDir(item.getFileType())) {
            System.err.println("File is not a directory. Moving single file is handled at initiation already. Skipping...");
            return;
        }
        FileSystemItem parent = findById(newParentId);
        if (parent == null) {
            System.err.println("Parent not found. Skipping...");
            return;
        }
        if (FileType.isNotDir(parent.getFileType())) {
            System.err.println("Parent is not a directory. Skipping...");
            return;
        }

        // needing oldPath since item or source dir path is already updated to reflect changes
        // item is the source directory, we need to get all children and update their paths
        String childrenIdPrefix = oldPath + item.getId() + "/"; // all children in the directory
        String newIdPrefix = parent.getPath() + parent.getId() + "/";

        String anchoredRegex = "^" + Pattern.quote(childrenIdPrefix);
        Query query = new Query(Criteria.where(FileItemField.PATH).regex(anchoredRegex)); // find children
        AggregationUpdate update = AggregationUpdate.update()
                .set(FileItemField.PATH)
                .toValue(StringOperators.ReplaceOne.valueOf(FileItemField.PATH)
                        .find(oldPath) // find old path prefix and replace with new path prefix
                        .replacement(newIdPrefix));
        mongoTemplate.updateMulti(query, update, FileSystemItem.class);

        Set<String> commonIds = getCommonIds(oldPath + item.getId() + parent.getPath() + parent.getId());
        releaseLockFileItem(commonIds);
    }

    private Set<String> getCommonIds(String idPath) {
        String[] idList = idPath.split("/");
        Set<String> commonIds = new HashSet<>(Arrays.asList(idList));
        commonIds.remove("");
        commonIds.remove(getROOT_FOLDER_ID());
        return commonIds;
    }

    private void lockFileItem(String userId, Set<String> fileIds) {
        if (fileIds.isEmpty()) return;
        List<FolderLocks> folderLocks = fileIds.stream().map(i -> new FolderLocks(i, userId)).toList();
        mongoTemplate.insert(folderLocks, FolderLocks.class);
    }

    private void releaseLockFileItem(Set<String> fileIds) {
        if (fileIds.isEmpty()) return;
        Query query = new Query(Criteria.where(FileItemField.ID).in(fileIds));
        mongoTemplate.remove(query, FolderLocks.class);
    }

    private FolderLocks checkIfFileItemInLock(Set<String> fileIds) {
        if (fileIds.isEmpty()) return null;
        Query query = new Query(Criteria.where("id").in(fileIds));
        return mongoTemplate.findOne(query, FolderLocks.class);
    }


    private boolean itemWithNameExists(String parentId, String name) {
        Query query = new Query(Criteria
                .where(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name));
        return mongoTemplate.exists(query, FileSystemItem.class);
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public UpdateResult updateFileMetadataAsMedia(String fileId, long mediaId, FileType fileType, String thumbnailObject,
                                                  int length, int width, int height) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update()
                .set(FileItemField.MEDIA_ID, mediaId)
                .set(FileItemField.FILE_TYPE, fileType)
                .set(FileItemField.THUMBNAIL, thumbnailObject)
                .set(FileItemField.LENGTH, length)
                .set(FileItemField.RESOLUTION_INFO, new FileSystemItem.ResolutionInfo(width, height))
                .unset(FileItemField.STATUS_CODE);
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    public String getFullPathInName(FileSystemItem item) {
        String pathInId = item.getPath();
        List<String> pathIds = Arrays.stream(pathInId.split("/"))
                .filter(s -> !s.isEmpty())
                .toList();

        if (pathIds.isEmpty()) return item.getName();

        List<FileSystemItem> parents = mongoTemplate.find(new Query(Criteria.where("id").in(pathIds)), FileSystemItem.class);

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

    public String getCachedElseDbDirectoryId(String parentId, String dirName, String userId) {
        String dirKey = "DIR_" + dirName + "|" + parentId;
        var cached = (ApplicationConfig.DirectoryCached) directoryIdCache.asMap().get(dirKey);
        if (cached == null) {
            Query query = Query.query(Criteria
                    .where(FileItemField.PARENT_ID).is(parentId)
                    .and(FileItemField.NAME).is(dirName));
            Update update = new Update().set(FileItemField.STATUS_CODE, FileStatus.IN_USE.getValue());
            FileSystemItem dir = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), FileSystemItem.class);
            if (dir == null)
                return null;
            directoryIdCache.asMap().computeIfAbsent(dirKey, k -> {
                Set<String> users = ConcurrentHashMap.newKeySet();
                users.add(userId);
                return new ApplicationConfig.DirectoryCached(dir.getId(), users);
            });
            return dir.getId();
        }
        return cached.dirId();
    }


    private void updateStatusCodeAndGet(String fileId, FileStatus status) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set(FileItemField.STATUS_CODE, status.getValue());
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    public void removeFileStatus(String fileId) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().unset(FileItemField.STATUS_CODE);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    // since directories get set as in-use in caching for folder upload - reset all at startup for dangling status
    @EventListener(ApplicationReadyEvent.class)
    public void resetAllFileInUseStatus() {
        mongoTemplate.updateMulti(
                Query.query(Criteria
                        .where(FileItemField.STATUS_CODE).is(FileStatus.IN_USE.getValue())),
                new Update().unset(FileItemField.STATUS_CODE), FileSystemItem.class);
    }


    public ApplicationConfig.DirectoryCached getCachedOrCreateDirectory(String dirKey, String dirName, String dirParentId, String dirPath, String userId) {
        return (ApplicationConfig.DirectoryCached) directoryIdCache.asMap().compute(dirKey, (_, existing) -> {
            if (existing == null) {
                String fileId = getOrCreateFolder(dirName, dirParentId, dirPath, FileType.DIR);
                Set<String> users = ConcurrentHashMap.newKeySet();
                users.add(userId);
                return new ApplicationConfig.DirectoryCached(fileId, users);
            } else {
                ((ApplicationConfig.DirectoryCached) existing).userUsing().add(userId);
                return existing;
            }
        });
    }

    public void addDirectoryToUserUsingList(String userId, String dirKey) {
        directoryIdCache.asMap().compute(userId, (_, existing) -> {
            if (existing == null) {
                Set<String> dirUserUsing = ConcurrentHashMap.newKeySet();
                dirUserUsing.add(dirKey); // add dirKey to get dir info from cache back (not using the dirId)
                return new ApplicationConfig.UserDirUsing(dirUserUsing);
            } else {
                ((ApplicationConfig.UserDirUsing) existing).dirUserUsing().add(dirKey);
                return existing;
            }
        });
    }

    public void removeAllDirectoriesUserUsing(String userId) {
        directoryIdCache.asMap().computeIfPresent(userId, (_, value) -> {
            for (String dirKey : ((ApplicationConfig.UserDirUsing) value).dirUserUsing()) {
                directoryIdCache.asMap().computeIfPresent(dirKey, (_, dirValue) -> {
                    var dirCached = (ApplicationConfig.DirectoryCached) dirValue;
                    dirCached.userUsing().remove(userId);

                    if (dirCached.userUsing().isEmpty()) {
                        removeFileStatus(dirCached.dirId());
                        return null; // null to remove the key
                    }
                    return dirValue;
                });
            }
            return null;
        });
    }



    public FileSystemItem findByMId(long mId) {
        Query query = new Query(Criteria.where(FileItemField.MEDIA_ID).is(mId));
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }

    private FileSystemItem getFileSystemItem(String id) {
        return fileSystemRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("File not found with id: " + id)
        );
    }

    public FileSystemItem findById(String id) {
        return mongoTemplate.findById(id, FileSystemItem.class);
    }

    public String getRootFolderName() {
        return mediaPath;
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

    public String getROOT_PATH() {
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
