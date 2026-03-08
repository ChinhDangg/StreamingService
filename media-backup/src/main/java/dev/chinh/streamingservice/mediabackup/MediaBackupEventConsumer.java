package dev.chinh.streamingservice.mediabackup;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediabackup.config.KafkaRedPandaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
public class MediaBackupEventConsumer {

    private final MinIOService minIOService;

    @Value( "${media.backup.path}")
    private String MEDIA_BACKUP_PATH;

    @Value("${backup.enabled}")
    private String backupEnabled;

    private void onCreateFile(MediaUpdateEvent.FileCreated event) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received create backup file event: " + event.fileName());
        try {
            String target = OSUtil.normalizePath(MEDIA_BACKUP_PATH + "/" + ContentMetaData.MEDIA_BUCKET, event.fileName());
            Path targetPath = Paths.get(target);
            if (Files.exists(targetPath)) {
                System.err.println("File already exists: " + target);
                return;
            }
            try (InputStream inputStream = minIOService.getFile(event.bucket(), event.objectName())) {
                Path parentPath = targetPath.getParent();
                if (parentPath != null && !Files.exists(parentPath))
                    Files.createDirectories(parentPath);

                Files.copy(inputStream, targetPath);
            } catch (FileAlreadyExistsException e) {
                System.err.println("File already exists: " + event.fileName());
                // do nothing - just log
            }
        } catch (Exception e) {
            System.err.println("Failed to create backup file: " + event.fileName());
            throw e;
        }
    }

    private void onDeleteFile(MediaUpdateEvent.FileDeleted event) throws IOException {
        System.out.println("Received delete backup file");
        try {
            Path path = Path.of(OSUtil.normalizePath(MEDIA_BACKUP_PATH + "/", event.fileName()));
            if (event.isNotDirectory()) {
                Files.deleteIfExists(path);
            } else {
                FileUtils.deleteDirectory(new File(path.toString()));
            }
        } catch (Exception e) {
            System.err.println("Failed to delete backup file: " + event.fileName());
            throw e;
        }
    }

    private void onCreatedMedia(MediaUpdateEvent.MediaCreatedReady event) throws Exception {
        System.out.println("Received media created ready event to backup thumbnail: " + event.mediaId() + ", thumbnail: " + event.thumbnail());
        if (event.thumbnail() == null) {
            System.out.println("Thumbnail is null, skipping thumbnail backup for media: " + event.mediaId());
            return;
        }
        createThumbnailBackup(event.thumbnail());
    }

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) throws Exception {
        boolean sameName = event.newThumbnail().equals(event.oldThumbnail());
        // overwrite old one if same name then no need to delete old thumbnail - passing null for old
        updateThumbnail(event.mediaId(), sameName ? null : event.oldThumbnail(), event.newThumbnail());
    }


    private void onThumbnailDeleted(MediaUpdateEvent.ThumbnailDeleted event) {
        deleteThumbnailBackup(event.objectName());
    }


    private void onNameEntityCreated(MediaUpdateEvent.NameEntityCreated event) throws Exception {
        System.out.println("Received name entity create backup event: " + event.nameEntityId());
        if (event.thumbnailPath() != null)
            createThumbnailBackup(event.thumbnailPath());
    }

    private void onUpdateNameEntity(MediaUpdateEvent.NameEntityUpdated event) throws Exception {
        boolean sameName = event.newThumbnail().equals(event.oldThumbnail());
        updateThumbnail(event.nameEntityId(), sameName ? null : event.oldThumbnail(), event.newThumbnail());
    }

    private void onNameEntityDeleted(MediaUpdateEvent.NameEntityDeleted event) {
        System.out.println("Received name entity delete backup event: " + event.nameEntityId());
        if (event.thumbnailPath() != null)
            deleteThumbnailBackup(event.thumbnailPath());
    }

    private void updateThumbnail(long id, String oldThumbnail, String newThumbnail) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received thumbnail update backup event: " + id + ", old: " + oldThumbnail + ", new: " + newThumbnail);
        if (newThumbnail != null) {
            createThumbnailBackup(newThumbnail);
        }
        if (oldThumbnail != null) {
            deleteThumbnailBackup(oldThumbnail);
        }
    }


    private void createThumbnailBackup(String newThumbnail) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received thumbnail create backup event: " + newThumbnail);
        String thumbnail = OSUtil.normalizePath(MEDIA_BACKUP_PATH + "/" + ContentMetaData.THUMBNAIL_BUCKET, newThumbnail);
        Path thumbnailPath = Paths.get(thumbnail);

        try (InputStream inputStream = minIOService.getFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnail)){
            Path parentPath = thumbnailPath.getParent();
            if (parentPath != null && !Files.exists(parentPath))
                Files.createDirectories(parentPath);

            Files.copy(inputStream, thumbnailPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("Failed to back up thumbnail: " + newThumbnail);
            throw e;
        }
    }

    private void deleteThumbnailBackup(String oldThumbnail) {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received thumbnail delete backup event, old: " + oldThumbnail);
        try {
            Path path = Paths.get(MEDIA_BACKUP_PATH + "/" + ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
            System.out.println("Deleted: " + Files.deleteIfExists(path));
        } catch (IOException e) {
            System.err.println("Failed to delete thumbnail file: " + oldThumbnail);
        }
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_OBJECT_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC,
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            switch (event) {
                case MediaUpdateEvent.FileCreated e -> onCreateFile(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteFile(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCreatedMedia(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);

                case MediaUpdateEvent.ThumbnailDeleted e -> onThumbnailDeleted(e);

                case MediaUpdateEvent.NameEntityCreated e -> onNameEntityCreated(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onNameEntityDeleted(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntity(e);
                default ->
                    // unknown event type → log and skip
                        System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println("Failed to handle media backup event: " + event + " " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }



    // listen to DLQ and print out the event details for now
    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_BACKUP_DLQ_TOPIC,
            groupId = "media-backup-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage == null ? "No error message found" : new String(errorMessage));

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.FileCreated e ->
                    System.out.println("Received create backup file event: " + e.fileName());
            case MediaUpdateEvent.FileDeleted e ->
                    System.out.println("Received delete backup file: " + e.fileName());
            case MediaUpdateEvent.MediaCreatedReady e ->
                    System.out.println("Received media created ready event to backup thumbnail: " + e.mediaId() + ", thumbnail: " + e.thumbnail());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    System.out.println("Received media thumbnail updated backup event: " + e.mediaId() +  " new: " + e.newThumbnail());

            case MediaUpdateEvent.ThumbnailDeleted e ->
                    System.out.println("Received thumbnail delete backup event: " + e.objectName());

            case MediaUpdateEvent.NameEntityCreated e ->
                    System.out.println("Received name entity create backup event: " + e.nameEntityId() + " thumbnail: " + e.thumbnailPath());
            case MediaUpdateEvent.NameEntityDeleted e ->
                    System.out.println("Received name entity delete backup event: " + e.nameEntityId() + " thumbnail: " + e.thumbnailPath());
            case MediaUpdateEvent.NameEntityUpdated e ->
                    System.out.println("Received name entity thumbnail updated backup event: " + e.nameEntityId() + " old: " + e.oldThumbnail() + " new: " + e.newThumbnail());
            default -> {
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                    //ack.acknowledge(); // ack on poison event to skip it
            }
        }
        System.out.println("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }
}