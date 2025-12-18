package dev.chinh.streamingservice.searchindexer.config;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    public static final String MEDIA_GROUP_ID = "media-service";

    public static final String MEDIA_UPDATED_OPENSEARCH_TOPIC = "media-updated-opensearch-events";

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, MEDIA_GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "dev.chinh.streamingservice.common.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "true");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MediaUpdateEvent.class.getName());
        props.put(JsonDeserializer.KEY_DEFAULT_TYPE, String.class.getName());

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(Object.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // IMPORTANT: require manual ack
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // retry + DQL handler
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // for dlq only - not media events
    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>());
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    public static final String MEDIA_UPDATE_DLQ_TOPIC = "media-update-opensearch-dlq";

    @Bean
    public NewTopic mediaUpdateDlqTopic() {
        return TopicBuilder.name(MEDIA_UPDATE_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                        (record, ex) -> new org.apache.kafka.common.TopicPartition(
                                MEDIA_UPDATE_DLQ_TOPIC,
                                record.partition()
                        ));

        // retry every 1s, up to 5 times
        FixedBackOff backOff = new FixedBackOff(1000, 5);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Optionally tell it which exceptions should NOT be retried:
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
