package dev.chinh.streamingservice.mediabackup;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediabackup.config.KafkaRedPandaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaBackupEventConsumer {

    private final MinIOService minIOService;

    @Value( "${media.backup.location}")
    private String MEDIA_BACKUP_LOCATION;

    @Value("${backup.enabled}")
    private String backupEnabled;

    private void onCreateFile(MediaUpdateEvent.FileCreated event) throws Exception {
        log.info("Received create backup file event: {}", event.fileName());
        try {
            String target = addBackupLocationToPath(addRootToPath(event.userId() + "/" + event.fileName()));
            Path targetPath = Paths.get(target);
            if (Files.exists(targetPath)) {
                log.error("File already exists: {}", target);
                return;
            }
            try (InputStream inputStream = minIOService.getFile(event.bucket(), event.objectName())) {
                Path parentPath = targetPath.getParent();
                if (parentPath != null && !Files.exists(parentPath))
                    Files.createDirectories(parentPath);

                Files.copy(inputStream, targetPath);
            } catch (FileAlreadyExistsException e) {
                log.error("File already exists: {}", event.fileName());
                // do nothing - just log
            }
        } catch (Exception e) {
            log.error("Failed to create backup file: {}", event.fileName());
            throw e;
        }
    }

    private void onDeleteFile(MediaUpdateEvent.FileDeleted event) throws IOException {
        log.info("Received delete backup file: {}", event.fileName());
        try {
            Path path = Path.of(addBackupLocationToPath(addRootToPath(event.fileName())));
            if (event.isNotDirectory()) {
                Files.deleteIfExists(path);
            } else {
                FileUtils.deleteDirectory(new File(path.toString()));
            }
        } catch (Exception e) {
            log.error("Failed to delete backup file: {}", event.fileName());
            throw e;
        }
    }

    private void onCreatedMedia(MediaUpdateEvent.MediaCreatedReady event) throws Exception {
        log.info("Received media created ready event to backup thumbnail: {}, thumbnail: {}", event.mediaId(), event.thumbnail());
        if (event.thumbnail() == null) {
            log.info("Thumbnail is null, skipping thumbnail backup for media: {}", event.mediaId());
            return;
        }
        createThumbnailBackup(event.thumbnail());
    }

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) throws Exception {
        boolean sameName = event.newThumbnail().equals(event.oldThumbnail());
        // overwrite old one if same name then no need to delete old thumbnail - passing null for old
        updateThumbnail(event.mediaId(), sameName ? null : event.oldThumbnail(), event.newThumbnail());
    }


    private void onThumbnailDeleted(MediaUpdateEvent.ThumbnailDeleted event) throws IOException {
        deleteThumbnailBackup(event.objectName());
    }


    private void onNameEntityCreated(MediaUpdateEvent.NameEntityCreated event) throws Exception {
        log.info("Received name entity create backup event: {}", event.nameEntityId());
        if (event.thumbnailPath() != null)
            createThumbnailBackup(event.thumbnailPath());
    }

    private void onUpdateNameEntity(MediaUpdateEvent.NameEntityUpdated event) throws Exception {
        boolean sameName = event.newThumbnail().equals(event.oldThumbnail());
        updateThumbnail(event.nameEntityId(), sameName ? null : event.oldThumbnail(), event.newThumbnail());
    }

    private void onNameEntityDeleted(MediaUpdateEvent.NameEntityDeleted event) throws IOException {
        log.info("Received name entity delete backup event: {}", event.nameEntityId());
        if (event.thumbnailPath() != null)
            deleteThumbnailBackup(event.thumbnailPath());
    }

    private void updateThumbnail(long id, String oldThumbnail, String newThumbnail) throws Exception {
        log.info("Received thumbnail update backup event: {}, old: {}, new: {}", id, oldThumbnail, newThumbnail);
        if (newThumbnail != null) {
            createThumbnailBackup(newThumbnail);
        }
        if (oldThumbnail != null) {
            deleteThumbnailBackup(oldThumbnail);
        }
    }


    private void createThumbnailBackup(String newThumbnail) throws Exception {
        log.info("Received thumbnail create backup event: {}", newThumbnail);
        String thumbnail = addBackupLocationToPath(ContentMetaData.THUMBNAIL_BUCKET + "/" + newThumbnail);
        Path thumbnailPath = Paths.get(thumbnail);

        try (InputStream inputStream = minIOService.getFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnail)){
            Path parentPath = thumbnailPath.getParent();
            if (parentPath != null && !Files.exists(parentPath))
                Files.createDirectories(parentPath);

            Files.copy(inputStream, thumbnailPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("Failed to back up thumbnail: {}", newThumbnail);
            throw e;
        }
    }

    private void deleteThumbnailBackup(String oldThumbnail) throws IOException {
        log.info("Received thumbnail delete backup event, old: {}", oldThumbnail);
        try {
            Path path = Paths.get(addBackupLocationToPath(ContentMetaData.THUMBNAIL_BUCKET + "/" + oldThumbnail));
            log.info("Deleted: {}", Files.deleteIfExists(path));
        } catch (IOException e) {
            log.error("Failed to delete thumbnail file: {}", oldThumbnail);
            throw e;
        }
    }

    private void onCreateDirectory(MediaUpdateEvent.DirectoryCreated event) throws IOException {
        log.info("Received create directory: {} {}", event.fileId(), event.dirPath());
        try {
            String dirPath = addRootToPath(event.dirPath());
            Path targetParent = Paths.get(addBackupLocationToPath(dirPath));
            if (Files.notExists(targetParent)) {
                Files.createDirectories(targetParent);
            }
        } catch (Exception e) {
            log.error("Failed to create directory: {} : {}", event.fileId(), e.getMessage());
            throw e;
        }
    }

    private void onRenameFile(MediaUpdateEvent.FileRenamed event) throws Exception {
        log.info("Received rename file: {}", event.fileId());
        try {
            String basePath = addRootToPath(event.filePath());
            int lastSlash = basePath.lastIndexOf("/");
            Path source = Paths.get(addBackupLocationToPath(basePath));
            Path target = Paths.get(addBackupLocationToPath(
                    basePath.substring(0, lastSlash == -1 ? basePath.length() : lastSlash) + "/" + event.newFileName()
            ));
            Files.move(source, target);
        } catch (Exception e) {
            log.error("Failed to rename file: {} : {}", event.fileId(), e.getMessage());
            throw e;
        }
    }

    private void onMoveDirectory(MediaUpdateEvent.DirectoryMoved event) throws Exception {
        log.info("Received move directory: {}, old: {}, new: {}", event.fileId(), event.oldPath(), event.newPath());
        try {
            moveFile(event.oldPath(), event.newPath());
        } catch (Exception e) {
            log.error("Failed to move directory: {} : {}", event.fileId(), e.getMessage());
            throw e;
        }
    }

    private void onMoveFile(MediaUpdateEvent.FileMoved event) throws Exception {
        log.info("Received move file: {}, old: {}, new: {}", event.fileId(), event.oldPath(), event.newPath());
        try {
            moveFile(event.oldPath(), event.newPath());
        } catch (Exception e) {
            log.error("Failed to move file: {} : {}", event.fileId(), e.getMessage());
            throw e;
        }
    }

    private void moveFile(String oldPath, String newPath) throws Exception {
        oldPath = addRootToPath(oldPath);
        newPath = addRootToPath(newPath);
        Path source = Paths.get(addBackupLocationToPath(oldPath));
        Path target = Paths.get(addBackupLocationToPath(newPath));
        log.info("{} -> {}", source, target);
        Files.move(source, target.resolve(source.getFileName()));
    }

    private String addRootToPath(String path) {
        if (path.startsWith(ContentMetaData.MEDIA_BUCKET)) {
            return path;
        }
        return ContentMetaData.MEDIA_BUCKET + "/" + path;
    }

    private String addBackupLocationToPath(String path) {
        return OSUtil.normalizePath(MEDIA_BACKUP_LOCATION, path);
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_HANDLER_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_HANDLER_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC,
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            if (!Boolean.parseBoolean(backupEnabled)) {
                log.info("Backup is disabled -- Skipping {}", event.getClass().getSimpleName());
                ack.acknowledge();
                return;
            }
            switch (event) {
                case MediaUpdateEvent.FileCreated e -> onCreateFile(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteFile(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCreatedMedia(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);

                case MediaUpdateEvent.ThumbnailDeleted e -> onThumbnailDeleted(e);

                case MediaUpdateEvent.NameEntityCreated e -> onNameEntityCreated(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onNameEntityDeleted(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntity(e);

                case MediaUpdateEvent.DirectoryCreated e -> onCreateDirectory(e);
                case MediaUpdateEvent.FileRenamed e -> onRenameFile(e);
                case MediaUpdateEvent.DirectoryMoved e -> onMoveDirectory(e);
                case MediaUpdateEvent.FileMoved e -> onMoveFile(e);
                default ->
                    // unknown event type → log and skip
                        log.error("Unknown MediaUpdateEvent type: {}", event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle media backup event: {} {}", event, e.getMessage());
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
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) throws Exception {
        log.info("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage == null ? "No error message found" : new String(errorMessage));

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.FileCreated e -> 
                    log.info("Backup DLQ: Received create backup file event: {}", e.fileName());
            case MediaUpdateEvent.FileDeleted e -> 
                    log.info("Backup DLQ: Received delete backup file: {}", e.fileName());
            case MediaUpdateEvent.MediaCreatedReady e ->
                    log.info("Backup DLQ: Received media created ready event to backup thumbnail: {}, thumbnail: {}", e.mediaId(), e.thumbnail());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    log.info("Backup DLQ: Received media thumbnail updated backup event: {} new: {}", e.mediaId(), e.newThumbnail());

            case MediaUpdateEvent.ThumbnailDeleted e ->
                    log.info("Backup DLQ: Received thumbnail delete backup event: {}", e.objectName());

            case MediaUpdateEvent.NameEntityCreated e ->
                    log.info("Backup DLQ: Received name entity create backup event: {} thumbnail: {}", e.nameEntityId(), e.thumbnailPath());
            case MediaUpdateEvent.NameEntityDeleted e ->
                    log.info("Backup DLQ: Received name entity delete backup event: {} thumbnail: {}", e.nameEntityId(), e.thumbnailPath());
            case MediaUpdateEvent.NameEntityUpdated e ->
                    log.info("Backup DLQ: Received name entity thumbnail updated backup event: {} old: {} new: {}", e.nameEntityId(), e.oldThumbnail(), e.newThumbnail());

            case MediaUpdateEvent.DirectoryCreated e -> 
                    log.info("Backup DLQ: Received create directory: {}", e.fileId());
            case MediaUpdateEvent.FileRenamed e -> 
                    log.info("Backup DLQ: Received rename file: {}", e.fileId());
            case MediaUpdateEvent.DirectoryMoved e ->
                    log.info("Backup DLQ: Received move directory: {}, old: {}, new: {}", e.fileId(), e.oldPath(), e.newPath());
            case MediaUpdateEvent.FileMoved e ->
                    log.info("Backup DLQ: Received move file: {}, old: {}, new: {}", e.fileId(), e.oldPath(), e.newPath());
            default -> {
                log.error("Backup DLQ: Unknown MediaUpdateEvent type: {}", event.getClass());
                    //ack.acknowledge(); // ack on poison event to skip it
            }
        }
        log.info("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }
}