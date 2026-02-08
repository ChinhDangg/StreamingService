package dev.chinh.streamingservice.filemanager;

import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    private final String mediaPath = "media";

    @PostConstruct
    public void init() {
        filePath = filePath.replace("\\", "/");
        if (Files.notExists(Path.of(filePath))) {
            System.err.println("Media backup path does not exist for file managing. Exiting...");
            System.exit(1);
        }
        System.out.println("File path: " + filePath);
    }

    public List<FileSystemItem> findFilesAtRoot() {
        return fileSystemRepository.findByPath(mediaPath);
    }

    public List<FileSystemItem> findFilesInDirectory(String directory) {
        String error = FileSystemValidator.isValidPath(directory);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        System.out.println(mediaPath + "/" + directory);
        return fileSystemRepository.findByPath(mediaPath + "/" + directory);
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
