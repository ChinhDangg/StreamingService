package dev.chinh.streamingservice.event;

import dev.chinh.streamingservice.event.config.KafkaRedPandaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String mediaUpdateOpenSearchTopic = KafkaRedPandaConfig.mediaUpdatedOpenSearchTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        kafkaTemplate.send(mediaUpdateOpenSearchTopic, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaCreated event) {
        kafkaTemplate.send(mediaUpdateOpenSearchTopic, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        kafkaTemplate.send(mediaUpdateOpenSearchTopic, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        kafkaTemplate.send(mediaUpdateOpenSearchTopic, event);
    }
}
