package dev.chinh.streamingservice.mediaobject;

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
    public void publishCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaUpdateEnrichment event) {
        kafkaTemplate.send(event.mediaSearchTopic(), event.mediaCreatedEvent());
    }
}
