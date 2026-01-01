package dev.chinh.streamingservice.searchindexer.config;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

    public static final String MEDIA_GROUP_ID = "media-search-indexer-service";

    public static final String MEDIA_SEARCH_TOPIC = "media-search-events";

    @Bean
    public DefaultKafkaConsumerFactory<String, MediaUpdateEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, MEDIA_GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        JsonDeserializer<MediaUpdateEvent> jsonDeserializer = new JsonDeserializer<>(MediaUpdateEvent.class);
        jsonDeserializer.addTrustedPackages("dev.chinh.streamingservice.common.event");
        jsonDeserializer.setUseTypeHeaders(true);

        ErrorHandlingDeserializer<MediaUpdateEvent> valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
        ErrorHandlingDeserializer<String> keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());

        return new DefaultKafkaConsumerFactory<>(
                props,
                keyDeserializer,
                valueDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MediaUpdateEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, MediaUpdateEvent> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MediaUpdateEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // IMPORTANT: require manual ack
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // retry + DLQ handler
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // for dlq only - not media events
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MediaUpdateEvent> dlqListenerContainerFactory(
            ConsumerFactory<String, MediaUpdateEvent> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MediaUpdateEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // No Recoverer here: just log the error and stop retrying
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0)));
        return factory;
    }

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

    public static final String MEDIA_SEARCH_DLQ_TOPIC = "media-search-dlq";

    @Bean
    public NewTopic mediaUpdateDlqTopic() {
        return TopicBuilder.name(MEDIA_SEARCH_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "604800000") // keep dlq messages for 7 days
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                        (record, ex) -> new org.apache.kafka.common.TopicPartition(
                                MEDIA_SEARCH_DLQ_TOPIC,
                                -1
                        ));

        // retry every 2s, up to 5 times
        FixedBackOff backOff = new FixedBackOff(2000, 5);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // exceptions should NOT be retried:
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
