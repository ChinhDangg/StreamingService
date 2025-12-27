package dev.chinh.streamingservice.mediaupload.event;

import dev.chinh.streamingservice.common.event.MediaBackupEvent;
import dev.chinh.streamingservice.mediaupload.event.config.KafkaRedPandaConfig;
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

    private static final String MEDIA_UPDATED_OPENSEARCH_TOPIC = KafkaRedPandaConfig.MEDIA_UPDATED_OPENSEARCH_TOPIC;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaCreated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityCreated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityUpdated event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDeleteNameEntityIndexOpenSearch(MediaUpdateEvent.NameEntityDeleted event) {
        kafkaTemplate.send(MEDIA_UPDATED_OPENSEARCH_TOPIC, event);
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreateMediaBackup(MediaBackupEvent.MediaCreated event) {
        kafkaTemplate.send(KafkaRedPandaConfig.MEDIA_BACKUP_TOPIC, event);
    }
}
