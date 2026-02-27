package dev.chinh.streamingservice.mediaobject;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class MediaObjectEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record EventWrapper(String topic, Object event) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishEventListener(EventWrapper event) {
        kafkaTemplate.send(event.topic, event.event);
    }
}
