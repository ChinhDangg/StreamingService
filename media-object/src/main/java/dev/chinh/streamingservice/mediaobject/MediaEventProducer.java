package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreatedReadyMediaEvent(MediaUpdateEvent.MediaCreatedReady event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdatedMediaThumbnailEvent(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreatedReadyNameEntityEvent(MediaUpdateEvent.NameEntityCreatedReady event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC, event);
    }

}
