package dev.chinh.streamingservice.filemanager;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.event.MediaFileEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${media.backup.path}")
    private String filePath;

    private final FileSystemRepository fileSystemRepository;
    private final MongoTemplate mongoTemplate;
    private final MediaFileEventProducer eventPublisher;

    private final String mediaPath = "media";

    public static String rootFolderId = null;

    public record FileRootResult(String rootId, String rootName, List<FileSystemItem> children) {}

    public FileRootResult findFilesAtRoot() {
        List<FileSystemItem> items = fileSystemRepository.findByParentId(rootFolderId);
        return new FileRootResult(getRootFolderId(), mediaPath, items);
    }

    public List<FileSystemItem> findFilesInDirectory(String parentId) {
        return fileSystemRepository.findByParentId(parentId);
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String addFileAsVideo(String fileId) {
        FileSystemItem item = getFileSystemItem(fileId);
        if (item.getFileType() != FileType.VIDEO) {
            throw new IllegalArgumentException("File is not a video");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            return "Item is already marked as video";
        }
        fileSystemRepository.updateMId(fileId, -1L);
        String fileFullName = item.getPath() + item.getName();
        if (fileFullName.startsWith(mediaPath))
            fileFullName = fileFullName.substring(mediaPath.length() + 1);
        eventPublisher.publishCreatedFinishedMedia(new MediaUpdateEvent.FileToMediaInitiated(fileId, MediaType.VIDEO, fileFullName, item.getUploadDate()));
        return "Added as video";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
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
        fileSystemRepository.updateMId(fileId, -1L);
        String path = item.getPath();
        if (path.startsWith(mediaPath)) {
            path = path.substring(mediaPath.length() + 1);
        }
        eventPublisher.publishCreatedFinishedMedia(new MediaUpdateEvent.FileToMediaInitiated(fileId, MediaType.ALBUM, path, item.getUploadDate()));
        return "Added as album";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public UpdateResult updateFileToMedia(String fileId, long mediaId, FileType fileType, String thumbnailObject) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update()
                .set("mId", mediaId)
                .set("fileType", fileType)
                .set("thumbnail", thumbnailObject);
        return mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }


    private FileSystemItem getFileSystemItem(String id) {
        return fileSystemRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("File not found with id: " + id)
        );
    }

    public String getRootFolderName() {
        return mediaPath;
    }

    public String getRootFolderId() {
        if (rootFolderId != null) return rootFolderId;
        Query query = new Query(Criteria
                .where("name").is(mediaPath)
                .and("path").is("/")
                .and("fileType").is(FileType.DIR)
        );
        FileSystemItem item = mongoTemplate.findOne(query, FileSystemItem.class);

        if (item == null) throw new RuntimeException("Failed to find root folder");

        rootFolderId = item.getId();
        return rootFolderId;
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
        String error = FileSystemValidator.isValidPath(directory);
        if (error != null) {
            throw new IllegalArgumentException(error);
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
