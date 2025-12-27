package dev.chinh.streamingservice.mediabackup;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.event.MediaBackupEvent;
import dev.chinh.streamingservice.mediabackup.config.KafkaRedPandaConfig;
import io.minio.Result;
import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class MediaBackupEventConsumer {

    private final MinIOService minIOService;

    @Value( "${media.backup.path}")
    private String MEDIA_BACKUP_PATH;

    private void onMediaCreateBackup(MediaBackupEvent.MediaCreated event) throws Exception {
        System.out.println("Received media create backup event: " + event.path());

            if (event.mediaType() == MediaType.VIDEO) {
                try (InputStream inputStream = minIOService.getFile(event.bucket(), event.path())) {
                    String target = OSUtil.normalizePath(MEDIA_BACKUP_PATH, event.path());
                    Path targetPath = Paths.get(target);

                    Path parentPath = targetPath.getParent();
                    if (parentPath != null && !Files.exists(parentPath))
                        Files.createDirectories(parentPath);

                    Files.copy(inputStream, targetPath);
                } catch (Exception e) {
                    System.err.println("Failed to back up video: " + event.path());
                    throw e;
                }
            }
            else if (event.mediaType() == MediaType.ALBUM) {
                try {
                    backupAlbum(event.bucket(), event.path());
                } catch (Exception e) {
                    System.err.println("Failed to backup album: " + event.path());
                    throw e;
                }
            }
    }

    private void backupAlbum(String bucket, String albumPath) throws Exception {
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(bucket, albumPath);
        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            try (InputStream inputStream = minIOService.getFile(bucket, objectName)) {
                Path targetPath = Paths.get(OSUtil.normalizePath(MEDIA_BACKUP_PATH, objectName));
                Path parentPath = targetPath.getParent();

                if (parentPath != null && !Files.exists(parentPath))
                    Files.createDirectories(parentPath);

                Files.copy(inputStream, targetPath);
            }
        }
    }

    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_BACKUP_TOPIC, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(MediaBackupEvent event, Acknowledgment acknowledgment) throws Exception {
        try {
            if (event instanceof MediaBackupEvent.MediaCreated e) {
                onMediaCreateBackup(e);
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            System.err.println("Failed to handle media backup event: " + event + " " + e.getMessage());
            throw e;
        }
    }


}
