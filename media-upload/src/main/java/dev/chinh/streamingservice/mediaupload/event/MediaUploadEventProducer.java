package dev.chinh.streamingservice.mediaupload.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaUploadEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record EventWrapper(String topic, Object event) {}
    public record ImmediateEventWrapper(String topic, Object event) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishTransactionalEventListener(EventWrapper event) {
        kafkaTemplate.send(event.topic, event.event);
    }

    @EventListener
    public void publishImmediateEvent(ImmediateEventWrapper event) {
        kafkaTemplate.send(event.topic, event.event);
    }
}
