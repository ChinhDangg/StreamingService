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

    public void publishCreatedFinishedMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        kafkaTemplate.send(EventTopics.MEDIA_UPLOAD_TOPIC, event);
    }
}
