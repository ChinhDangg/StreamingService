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
    public void publishCreatedMediaEvent(MediaUpdateEvent.MediaCreated event) {
        kafkaTemplate.send(EventTopics.MEDIA_CREATED_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdatedMediaThumbnailEvent(MediaUpdateEvent.MediaThumbnailUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_THUMBNAIL_UPDATED_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateThumbnailEvent(MediaUpdateEvent.ThumbnailUpdated event) {
        kafkaTemplate.send(EventTopics.THUMBNAIL_UPDATED_TOPIC, event);
    }

}
