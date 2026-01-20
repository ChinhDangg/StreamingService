package dev.chinh.streamingservice.mediaupload.event;

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
    public void publishUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_SEARCH_TOPIC, event);
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaEnrichment(MediaUpdateEvent.MediaCreated event) {
        kafkaTemplate.send(EventTopics.MEDIA_OBJECT_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) {
        kafkaTemplate.send(EventTopics.MEDIA_OBJECT_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityCreated event) {
        kafkaTemplate.send(EventTopics.MEDIA_OBJECT_TOPIC, event);
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDeleteMediaEvent(MediaUpdateEvent.MediaDeleted event) {
        kafkaTemplate.send(EventTopics.MEDIA_ALL_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDeleteNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityDeleted event) {
        kafkaTemplate.send(EventTopics.MEDIA_ALL_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateNameEntityThumbnail(MediaUpdateEvent.NameEntityThumbnailUpdatedReady event) {
        kafkaTemplate.send(EventTopics.MEDIA_ALL_TOPIC, event);
    }
}
