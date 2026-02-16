package dev.chinh.streamingservice.filemanager.event;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import lombok.AllArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@AllArgsConstructor
public class MediaFileEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateFinishedMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        kafkaTemplate.send(EventTopics.MEDIA_UPLOAD_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDeleteMediaFile(MediaUpdateEvent.MediaFileDeleted event) {
        kafkaTemplate.send(EventTopics.MEDIA_FILE_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDeleteMedia(MediaUpdateEvent.MediaDeleted event) {
        kafkaTemplate.send(EventTopics.MEDIA_OBJECT_AND_BACKUP_TOPIC, event);
    }
}
