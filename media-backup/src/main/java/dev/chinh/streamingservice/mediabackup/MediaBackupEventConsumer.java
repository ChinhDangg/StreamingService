package dev.chinh.streamingservice.mediabackup;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediabackup.config.KafkaRedPandaConfig;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class MediaBackupEventConsumer {

    private final MinIOService minIOService;

    @Value( "${media.backup.path}")
    private String MEDIA_BACKUP_PATH;

    @Value("${backup.enabled}")
    private String backupEnabled;

    private void onMediaCreateBackup(MediaUpdateEvent.MediaCreatedReady event) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received media create backup event: " + event.absolutePath());

        if (event.mediaType() == MediaType.VIDEO) {
            String target = OSUtil.normalizePath(MEDIA_BACKUP_PATH, event.absolutePath());
            Path targetPath = Paths.get(target);
            if (Files.exists(targetPath)) {
                System.err.println("File already exists: " + target);
                return;
            }

            try (InputStream inputStream = minIOService.getFile(event.bucket(), event.path())) {
                Path parentPath = targetPath.getParent();
                if (parentPath != null && !Files.exists(parentPath))
                    Files.createDirectories(parentPath);

                Files.copy(inputStream, targetPath);
            } catch (FileAlreadyExistsException e) {
                System.err.println("File already exists: " + event.path());
                // do nothing - just log
            } catch (Exception e) {
                System.err.println("Failed to back up video: " + event.path());
                throw e;
            }
        }
        else if (event.mediaType() == MediaType.ALBUM) {
            try {
                backupAlbum(event.bucket(), event.path(), event.absolutePath());
            } catch (Exception e) {
                System.err.println("Failed to backup album: " + event.path());
                throw e;
            }
        } else if (event.mediaType() != MediaType.GROUPER) {
            System.err.println("Unknown media type: " + event.mediaType());
        }

        createThumbnailBackup(event.thumbnail());
    }

    private void backupAlbum(String bucket, String albumPath, String absolutePath) throws Exception {
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(bucket, albumPath);
        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            try (InputStream inputStream = minIOService.getFile(bucket, objectName)) {
                Path targetPath = Paths.get(OSUtil.normalizePath(MEDIA_BACKUP_PATH, mergePaths(absolutePath, objectName)));
                Path parentPath = targetPath.getParent();

                if (parentPath != null && !Files.exists(parentPath))
                    Files.createDirectories(parentPath);

                Files.copy(inputStream, targetPath);
            } catch (FileAlreadyExistsException e) {
                System.err.println("File already exists: " + e.getMessage());
                // do nothing - skip - just log
            }
        }
    }

    public static String mergePaths(String absolute, String object) {
        Path absPath = Paths.get(absolute);
        Path objPath = Paths.get(object);

        int objParts = objPath.getNameCount();

        // We look for the longest sequence from the START of objPath
        // that matches the END of absPath.
        for (int i = objParts; i > 0; i--) {
            // Get a sub-sequence from the start of the object path:
            // e.g., "something1/something2", then "something1"
            Path sub = objPath.subpath(0, i);

            if (absPath.endsWith(sub)) {
                // If it matches, take the absolute path and
                // append only the REMAINING part of the object path.
                Path remaining = objPath.subpath(i, objParts);
                return absPath.resolve(remaining).toString().replace("\\", "/");
            }
        }

        // If no overlap is found, might want to join them directly
        // or handle it as a specific case.
        return absPath.resolve(objPath).toString().replace("\\", "/");
    }

    private void onMediaDeleteBackup(MediaUpdateEvent.MediaDeleted event) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received media delete backup event: " + event.absolutePath());
        Path path = Paths.get(MEDIA_BACKUP_PATH, event.absolutePath());
        if (event.mediaType() == MediaType.VIDEO) {
            Files.deleteIfExists(path);
        } else if (event.mediaType() == MediaType.ALBUM) {
            File directory = new File(path.toString());
            FileUtils.deleteDirectory(directory);
        } else {
            System.err.println("Unknown media type: " + event.mediaType());
        }

        deleteThumbnailBackup(event.thumbnail());
    }

    private void createThumbnailBackup(String newThumbnail) throws Exception {
        if (!Boolean.parseBoolean(backupEnabled)) {
            return;
        }
        System.out.println("Received thumbnail create backup event: " + newThumbnail);
        String thumbnail = OSUtil.normalizePath(MEDIA_BACKUP_PATH + "/" + ContentMetaData.THUMBNAIL_BUCKET, newThumbnail);
        Path thumbnailPath = Paths.get(thumbnail);
        if (Files.exists(thumbnailPath)) {
            System.err.println("File thumbnail already exists: " + newThumbnail);
            return;
        }

        try (InputStream inputStream = minIOService.getFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnail)){
            Path parentPath = thumbnailPath.getParent();
            if (parentPath != null && !Files.exists(parentPath))
                Files.createDirectories(parentPath);

            Files.copy(inputStream, thumbnailPath);
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

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) throws Exception {
        updateThumbnail(event.mediaId(), event.oldThumbnail(), event.newThumbnail());
    }

    private void onUpdateNameEntityThumbnail(MediaUpdateEvent.NameEntityThumbnailUpdatedReady event) throws Exception {
        updateThumbnail(event.nameEntityId(), event.oldThumbnail(), event.newThumbnail());
    }

    private void onNameEntityCreated(MediaUpdateEvent.NameEntityCreatedReady event) throws Exception {
        System.out.println("Received name entity create backup event: " + event.nameEntityId());
        createThumbnailBackup(event.thumbnailPath());
    }

    private void onNameEntityDeleted(MediaUpdateEvent.NameEntityDeleted event) {
        System.out.println("Received name entity delete backup event: " + event.nameEntityId());
        if (event.thumbnailPath() != null)
            deleteThumbnailBackup(event.thumbnailPath());
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_ALL_TOPIC,
            EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaDeleted e -> onMediaDeleteBackup(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onMediaCreateBackup(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);
                case MediaUpdateEvent.NameEntityCreatedReady e -> onNameEntityCreated(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onNameEntityDeleted(e);
                case MediaUpdateEvent.NameEntityThumbnailUpdatedReady e -> onUpdateNameEntityThumbnail(e);
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
                          @Header(name = "x-exception-message", required = false) String errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage);

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.MediaDeleted e ->
                    System.out.println("Received media delete backup event: " + e.absolutePath());
            case MediaUpdateEvent.MediaCreatedReady e ->
                    System.out.println("Received media create backup event: " + e.path());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    System.out.println("Received media thumbnail updated backup event: " + e.mediaId() + " old: " + e.oldThumbnail() + " new: " + e.newThumbnail());
            case MediaUpdateEvent.NameEntityCreatedReady e ->
                    System.out.println("Received name entity create backup event: " + e.nameEntityId());
            case MediaUpdateEvent.NameEntityDeleted e ->
                    System.out.println("Received name entity delete backup event: " + e.nameEntityId());
            case MediaUpdateEvent.NameEntityThumbnailUpdatedReady e ->
                System.out.println("Received name entity thumbnail updated backup event: " + e.nameEntityId() + " old: " + e.oldThumbnail() + " new: " + e.newThumbnail());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge(); // ack on poison event to skip it
            }
        }
        System.out.println("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }
}