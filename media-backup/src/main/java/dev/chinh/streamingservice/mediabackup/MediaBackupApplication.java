package dev.chinh.streamingservice.mediabackup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class MediaBackupApplication {

    @Value( "${media.backup.path}")
    private static String MEDIA_BACKUP_PATH;

    public static void main(String[] args) {
        if (Files.notExists(Path.of(MEDIA_BACKUP_PATH))) {
            // Use local dir to back up for now - later move to cluster if needed
            throw new RuntimeException("Media backup path does not exist: " + MEDIA_BACKUP_PATH);
        }
        SpringApplication.run(MediaBackupApplication.class, args);
    }

}
