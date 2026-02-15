package dev.chinh.streamingservice.filemanager.event;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import lombok.AllArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MediaFileEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreateFinishedMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        kafkaTemplate.send(EventTopics.MEDIA_UPLOAD_TOPIC, event);
    }

    public void publishDeleteMediaFile(MediaUpdateEvent.MediaFileDeleted event) {
        kafkaTemplate.send(EventTopics.MEDIA_FILE_TOPIC, event);
    }
}
