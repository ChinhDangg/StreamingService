package dev.chinh.streamingservice.filemanager;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.NonNull;
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

    public String getRootFolderId() {
        if (rootFolderId != null) return rootFolderId;
        Query query = new Query(Criteria
                .where("name").is(mediaPath)
                .and("path").is(",")
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
                .and("path").is(",")
                .and("fileType").is(FileType.DIR)
        );

        Update update = new Update()
                .setOnInsert("name", mediaPath)
                .setOnInsert("path", ",")
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
