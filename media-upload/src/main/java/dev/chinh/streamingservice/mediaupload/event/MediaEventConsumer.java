package dev.chinh.streamingservice.mediaupload.event;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.event.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class MediaEventConsumer {

    private final MediaUploadService mediaUploadService;

    @Transactional
    public void onInitiateFileToMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        System.out.println("Received file to media initiated event: " + event.fileId());
        try {
            MediaUploadService.MediaUploadRequest uploadRequest = new MediaUploadService.MediaUploadRequest(
                    event.objectName(), event.mediaType()
            );
            MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                    event.objectName().substring(event.objectName().lastIndexOf("/") + 1),
                    (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
            );
            mediaUploadService.saveMedia(uploadRequest, mediaBasicInfo, event.fileId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate file to media", e);
        }
    }

    @Transactional
    @KafkaListener(topics = {
            EventTopics.MEDIA_UPLOAD_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            if (event instanceof MediaUpdateEvent.FileToMediaInitiated) {
                onInitiateFileToMedia((MediaUpdateEvent.FileToMediaInitiated) event);
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


        System.out.println("======= =======");
    }
}
