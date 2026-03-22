package dev.chinh.streamingservice.mediaupload.event;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.event.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class MediaUploadEventConsumer {

    private final MediaUploadService mediaUploadService;
    private final MediaMetadataModifyService mediaMetadataModifyService;
    private final MediaUploadEventProducer producer;

    private void onInitiateFileToMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        System.out.println("Received file to media initiated event: " + event.fileId());
        try {
            if (event.childMediaId() != null) {
                if (event.parentMediaId() != null) {
                    String error = mediaUploadService.addMediaToGrouper(event.parentMediaId(), event.childMediaId(), event.fileName());
                    if (error != null)
                        System.err.println(error);
                    return;
                }
                System.out.println("File event is given with media ID but not part of a grouper, skipping file to media");
                return;
            }

            MediaUploadService.MediaUploadRequest uploadRequest = new MediaUploadService.MediaUploadRequest(
                    event.bucket(), event.objectName(), event.fileName(), event.mediaType(), event.searchable()
            );
            int lastDotIndex = event.fileName().lastIndexOf(".");
            lastDotIndex = lastDotIndex == -1 ? event.fileName().length() : lastDotIndex;
            MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                    event.fileName().substring(0, lastDotIndex).replaceAll("[-_]", " "),
                    (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
            );
            long mediaId = mediaUploadService.saveMedia(uploadRequest, mediaBasicInfo, event.parentMediaId());

            // upload service can send media enriched for a single file
            // for multiple files as one media like ALBUM or GROUPER need file service to retrieve all contents
            if (event.mediaType() == MediaType.VIDEO) {
                producer.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_OBJECT_TOPIC,
                        new MediaUpdateEvent.MediaEnriched(
                                event.fileId(),
                                mediaId,
                                event.mediaType(),
                                MediaUploadService.createMediaThumbnailString(event.mediaType(), mediaId, event.objectName()),
                                event.searchable(),
                                -1,
                                -1
                        )
                ));
            } else if (event.mediaType() == MediaType.ALBUM) {
                producer.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        new MediaUpdateEvent.DirectoryToMediaInitiated(
                                event.fileId(),
                                mediaId,
                                event.mediaType(),
                                event.searchable(),
                                MediaUploadService.createMediaThumbnailString(event.mediaType(), mediaId, event.objectName()),
                                0,
                                0
                        )
                ));
            } else if (event.mediaType() == MediaType.GROUPER) {
                producer.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        new MediaUpdateEvent.NestedDirectoryToMediaInitiated(
                                event.fileId(),
                                mediaId,
                                MediaType.GROUPER,
                                MediaType.ALBUM,
                                false,
                                MediaUploadService.createMediaThumbnailString(event.mediaType(), mediaId, event.objectName()),
                                0
                        )
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate file to media", e);
        }
    }

    private void onDeleteMedia(MediaUpdateEvent.FileDeleted event) {
        if (event.mediaId() == null) {
            System.out.println("Media ID is null, skipping delete media event");
            return;
        }
        System.out.println("Received delete media event: " + event.mediaId());
        try {
            mediaMetadataModifyService.deleteMedia(event.mediaId());
        } catch (Exception e) {
            System.err.println("Failed to delete media: " + event.mediaId());
            throw e;
        }
    }

    private void onMoveGrouperItem(MediaUpdateEvent.GrouperItemMoved event) {
        System.out.println("Received moving grouper item: " + event.childMediaId());
        try {
            if (event.parentMediaId() != null) {
                String error = mediaUploadService.addMediaToGrouper(event.parentMediaId(), event.childMediaId(), event.fileName());
                if (error != null)
                    System.err.println(error);
            } else {
                // a grouper item is moved out of a grouper and not into another grouper - delete media info
                mediaMetadataModifyService.deleteMedia(event.childMediaId());
            }
        } catch (Exception e) {
            System.err.println("Failed to move grouper item outside grouper: " + event.childMediaId());
            throw e;
        }
    }



    @KafkaListener(topics = {
            EventTopics.MEDIA_UPLOAD_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.FileToMediaInitiated e -> onInitiateFileToMedia(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteMedia(e);
                case MediaUpdateEvent.GrouperItemMoved e -> onMoveGrouperItem(e);
                default -> {
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_UPLOAD_DLQ_TOPIC,
            groupId = "media-upload-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        String message = errorMessage != null ? new String(errorMessage) : "No error message found";
        System.out.printf("Error Message: %s\n", message);
        switch (event) {
            case MediaUpdateEvent.FileToMediaInitiated e ->
                System.out.println("Received initiate file to media initiated event: " + e.fileId());
            case MediaUpdateEvent.FileDeleted e ->
                System.out.println("Received file delete event: " + e.fileId());
            case MediaUpdateEvent.GrouperItemMoved e ->
                System.out.println("Received move grouper item event: " + e.childMediaId());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
        }

        System.out.println("======= =======");
    }
}
