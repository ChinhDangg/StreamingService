package dev.chinh.streamingservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String updateLengthOpenSearchTopic = "update-media-length-opensearch";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        kafkaTemplate.send(updateLengthOpenSearchTopic, event);
    }

    public static final String createMediaOpenSearchTopic = "create-media-opensearch";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaCreated event) {
        kafkaTemplate.send(createMediaOpenSearchTopic, event);
    }

    public static final String updateMediaNameEntityOpenSearchTopic = "update-media-name-entity-opensearch";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        kafkaTemplate.send(updateMediaNameEntityOpenSearchTopic, event);
    }

    public static final String updateMediaTitleOpenSearchTopic = "update-media-title-opensearch";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        kafkaTemplate.send(updateMediaTitleOpenSearchTopic, event);
    }


    public static final String createNameEntityOpenSearchTopic = "create-name-entity-opensearch";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateNameEntityOpenSearch(MediaUpdateEvent.NameEntityCreated event) {
        kafkaTemplate.send(createNameEntityOpenSearchTopic, event);
    }
}
