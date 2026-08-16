package dev.chinh.streamingservice.mediahandler.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaHandlerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record EventWrapper(String topic, String eventKey, Object event) {}
    public record ImmediateEventWrapper(String topic, String eventKey, Object event) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishTransactionalEventListener(EventWrapper event) {
        kafkaTemplate.send(event.topic, event.eventKey, event.event);
    }

    @EventListener
    public void publishImmediateEvent(ImmediateEventWrapper event) {
        kafkaTemplate.send(event.topic, event.eventKey, event.event);
    }
}
