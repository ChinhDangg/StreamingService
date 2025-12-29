package dev.chinh.streamingservice.mediabackup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class AppStartRunner implements ApplicationRunner {

    @Value("${media.backup.path}")
    private String backupPath;

    @Value("${backup.enabled}")
    private String backupEnabled;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            System.out.println("Media backup is disabled. Exiting.");
            System.exit(1);
        }

        if (Files.notExists(Paths.get(backupPath))) {
            throw new RuntimeException("Media backup path does not exist");
        } else {
            System.out.println("Media backup path exists");
        }
    }
}
