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
import dev.chinh.streamingservice.filemanager.event.MediaFileEventProducer;
import dev.chinh.streamingservice.filemanager.repository.FileSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final FileSystemRepository fileSystemRepository;
    private final MongoTemplate mongoTemplate;
    private final ThumbnailService thumbnailService;

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
        return PageRequest.of(page, pageSize, Sort.by(sortOrder, sortBy.getField()));
    }


    public String initiateUploadRequest(String filePath) {
        var validatedFile = FileSystemValidator.isValidPath(filePath);
        if (validatedFile.errorMessage() != null) {
            throw new IllegalArgumentException(validatedFile.errorMessage());
        }
        String validatedPath = validatedFile.validatedPath();
        int firstSlash = validatedPath.indexOf("/");
        String firstName = validatedPath.substring(0, firstSlash == -1 ? validatedPath.length() : firstSlash);

        boolean exists = mongoTemplate.exists(new Query(Criteria
                        .where(FileItemField.PARENT_ID).is(getROOT_FOLDER_ID())
                        .and(FileItemField.NAME).is(firstName)),
                FileSystemItem.class);
        if (exists) throw new IllegalArgumentException(firstSlash == -1 ? "File" : "Folder" + " already exists: " + validatedPath);
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
        UpdateResult result = updateStatusCode(fileId, FileStatus.PROCESSING);
        if (result.getModifiedCount() == 0) {
            return "Item is already marked as processing";
        }

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
        UpdateResult result = updateStatusCode(fileId, FileStatus.PROCESSING);
        if (result.getModifiedCount() == 0) {
            return "Item is already marked as processing";
        }
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
        UpdateResult result = updateStatusCode(fileId, FileStatus.PROCESSING);
        if (result.getModifiedCount() == 0) {
            return "Item is already marked as processing";
        }
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

    private UpdateResult updateStatusCode(String fileId, FileStatus status) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set(FileItemField.STATUS_CODE, status.getValue());
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
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
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(newFolderName)
                .and(FileItemField.PARENT_ID).is(parentId));
        if (mongoTemplate.exists(query, FileSystemItem.class))
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
        updateStatusCode(fileId, FileStatus.DELETING);
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
        updateStatusCode(item.getId(), FileStatus.DELETING);

        producer.publishEvent(new MediaFileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileDeleted(
                        item.getId(), getFullPathInName(item), FileType.isNotDir(item.getFileType()), item.getMId())
        ));
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

    // need the returning path to start and end with "/"
    public String getPathForFileItem(String parentPath, String currentPath) {
        if (parentPath.startsWith(getROOT_PATH()))
            return OSUtil.normalizePath(parentPath, currentPath + "/");
        return OSUtil.normalizePath(getROOT_PATH(), parentPath + "/" + currentPath + "/");
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
