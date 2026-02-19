package dev.chinh.streamingservice.filemanager;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${media.backup.path}")
    private String filePath;

    private final FileSystemRepository fileSystemRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private final String mediaPath = ContentMetaData.MEDIA_BUCKET;
    private final String rootPath = "/" + mediaPath + "/";

    public static String ROOT_FOLDER_ID = null;

    public record FileRootResult(String rootId, String rootName, Page<FileSystemItem> children) {}

    public FileRootResult findFilesAtRoot(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        Page<FileSystemItem> items = fileSystemRepository.findByParentId(ROOT_FOLDER_ID, getPageable(offset, sortBy, sortOrder));
        return new FileRootResult(getROOT_FOLDER_ID(), mediaPath, items);
    }

    public Page<FileSystemItem> findFilesInDirectory(String parentId, int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return fileSystemRepository.findByParentId(parentId, getPageable(offset, sortBy, sortOrder));
    }

    private Pageable getPageable(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        final int pageSize = 25;
        return PageRequest.of(offset, pageSize, Sort.by(sortOrder, sortBy.getField()));
    }

    // using codeStatus as file status:
    // -1 - processing to be added as media
    // -2 - marked as deleted

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addFileAsVideo(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getFileType() != FileType.VIDEO) {
            throw new IllegalArgumentException("File is not a video");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            return "Item is already marked as video";
        }
        UpdateResult result = updateStatusCode(fileId, -1L);
        if (result.getModifiedCount() == 0) {
            return "Item is already marked as processing";
        }
        eventPublisher.publishEvent(new MediaUpdateEvent.FileToMediaInitiated(
                fileId, MediaType.VIDEO, getFileItemPathAsObjectName(item.getPath(), item.getName()), item.getUploadDate()));
        return "Processing as video";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addDirectoryAsAlbum(String fileId) {
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
        UpdateResult result = updateStatusCode(fileId, -1L);
        if (result.getModifiedCount() == 0) {
            return "Item is already marked as processing";
        }
        eventPublisher.publishEvent(new MediaUpdateEvent.FileToMediaInitiated(
                fileId, MediaType.ALBUM, getFileItemPathAsObjectName(item.getPath(), item.getName()), item.getUploadDate()));
        return "Processing as album";
    }

    private UpdateResult updateStatusCode(String fileId, long code) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().set("statusCode", code);
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    // need the returning path to NOT start and end with "/"
    // assume name is filename and contains no "/"
    private String getFileItemPathAsObjectName(String path, String name) {
        if (path.startsWith(rootPath)) {
            int begin = rootPath.length(); // to start after "/"
            if (begin == path.length()) // path probably == root path
                return name;
            return OSUtil.normalizePath(path.substring(begin), name);
        }
        String p = OSUtil.normalizePath(path, name);
        if (p.startsWith("/")) return p.substring(1);
        return p;
    }

    // need the returning path to start and end with "/"
    public String getPathForFileItem(String path, String name) {
        if (path.startsWith(rootPath))
            return OSUtil.normalizePath(rootPath, name + "/");
        return OSUtil.normalizePath(rootPath, path + "/" + name + "/");
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public UpdateResult updateFileMetadataAsMedia(String fileId, long mediaId, FileType fileType, String thumbnailObject) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update()
                .set("mId", mediaId)
                .set("fileType", fileType)
                .set("thumbnail", thumbnailObject)
                .unset("statusCode");
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    @Transactional
    public void initiateDeleteFile(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getStatusCode() == -2)
            return;
        if (item.getFileType() == FileType.DIR) {
            boolean anyChildMedia = mongoTemplate.exists(
                    new Query(Criteria
                            .where("path").regex("^" + item.getPath() + item.getName() + "/")
                            .and("mId").nin(null, 0)),
                    FileSystemItem.class);
            if (anyChildMedia) {
                throw new IllegalArgumentException("Directory is not empty - include media item");
            }
        }
        updateStatusCode(fileId, -2L);
        if (item.getMId() == null || item.getMId() == 0) {
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaFileDeleted(fileId));
            // send delete to backup and object service to delete backup file and object
            String objectName = getFileItemPathAsObjectName(item.getPath(), item.getName());
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaDeleted(
                    -1,
                    ContentMetaData.MEDIA_BUCKET,
                    objectName,
                    false,
                    null,
                    item.getFileType() == FileType.DIR ? MediaType.ALBUM : MediaType.VIDEO,
                    OSUtil.normalizePath(mediaPath, objectName)
            ));
        } else if (item.getMId() > 0) {
            throw new IllegalArgumentException("File is already marked as media - delete through media item instead: " + item.getMId());
        }
    }

    // to be called by kafka listener consumer
    @Transactional
    public void deleteFile(String fileId) {
        FileSystemItem fileItem = findById(fileId);
        if (fileItem == null) return;
        if (fileItem.getFileType() == FileType.DIR) {
            String parentPath = Pattern.quote(getPathForFileItem(fileItem.getPath(), fileItem.getName()));
            // remove all children where path starts with parentPath
            mongoTemplate.remove(new Query(Criteria
                    .where("path").regex("^" + parentPath)), FileSystemItem.class);
        }
        mongoTemplate.remove(new Query(Criteria.where("id").is(fileId)), FileSystemItem.class);
    }

    // to be called by kafka listener consumer
    @Transactional
    public void deleteMedia(long mediaId) {
        FileSystemItem fileItem = findByMId(mediaId);
        if (fileItem.getFileType() == FileType.ALBUM) {
            String parentPath = Pattern.quote(getPathForFileItem(fileItem.getPath(), fileItem.getName()));
            // remove all children where path starts with parentPath
            mongoTemplate.remove(new Query(Criteria
                    .where("path").regex("^" + parentPath)), FileSystemItem.class);
        }
        mongoTemplate.remove(new Query(Criteria.where("id").is(fileItem.getId())), FileSystemItem.class);
    }




    public FileSystemItem findByMId(long mId) {
        Query query = new Query(Criteria.where("mId").is(mId));
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }

    private FileSystemItem getFileSystemItem(String id) {
        return fileSystemRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("File not found with id: " + id)
        );
    }

    private FileSystemItem findById(String id) {
        return mongoTemplate.findById(id, FileSystemItem.class);
    }

    public String getRootFolderName() {
        return mediaPath;
    }

    public String getROOT_FOLDER_ID() {
        if (ROOT_FOLDER_ID != null) return ROOT_FOLDER_ID;
        Query query = new Query(Criteria
                .where("name").is(mediaPath)
                .and("path").is("/")
                .and("fileType").is(FileType.DIR)
        );
        FileSystemItem item = mongoTemplate.findOne(query, FileSystemItem.class);

        if (item == null) throw new RuntimeException("Failed to find root folder");

        ROOT_FOLDER_ID = item.getId();
        return ROOT_FOLDER_ID;
    }

    public void createRootFolder() {
        Query query = new Query(Criteria
                .where("name").is(mediaPath)
                .and("path").is("/")
                .and("fileType").is(FileType.DIR)
        );

        Update update = new Update()
                .setOnInsert("name", mediaPath)
                .setOnInsert("path", "/")
                .setOnInsert("fileType", FileType.DIR);

        UpdateResult result = mongoTemplate.upsert(query, update, FileSystemItem.class);
        if (!result.wasAcknowledged()) throw new RuntimeException("Failed to create root folder");
    }


    public List<FileResult> getFilesAtRoot() throws IOException {
        return visitDirectory(Path.of(filePath, mediaPath));
    }

    public List<FileResult> getFilesInDirectory(String directory) throws IOException {
        var validationResult = FileSystemValidator.isValidPath(directory);
        if (validationResult.errorMessage() != null) {
            throw new IllegalArgumentException(validationResult.errorMessage());
        }
        return visitDirectory(Path.of(filePath, mediaPath, directory));
    }

    private List<FileResult> visitDirectory(Path path) throws IOException {
        List<FileResult> fileVisited = new ArrayList<>();
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) {
                if (dir.equals(path))
                    return FileVisitResult.CONTINUE;
                fileVisited.add(new FileResult(
                        FileType.DIR,
                        dir.getFileName().toString()
                ));
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                fileVisited.add(new FileResult(
                        FileType.FILE,
                        file.getFileName().toString()
                ));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc) throws IOException {
                fileVisited.add(new FileResult(
                        FileType.ERROR,
                        file.getFileName().toString()
                ));
                return super.visitFileFailed(file, exc);
            }
        });
        return fileVisited;
    }
}
