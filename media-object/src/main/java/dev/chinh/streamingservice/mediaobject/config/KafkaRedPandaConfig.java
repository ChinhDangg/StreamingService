package dev.chinh.streamingservice.mediaobject.config;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.file.FileAlreadyExistsException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

    public static final String MEDIA_GROUP_ID = "media-service";

    public static final String MEDIA_UPDATED_OBJECT_TOPIC = "media-updated-object-events";

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

        // retry only
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(2000L, 3);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            // This logic runs AFTER all retries fail (Recovery Logic)
            System.err.println("Failed to process record: " + record.key() + " after 3 attempts");
        }, fixedBackOff);

        // List exceptions DON'T want to retry
        handler.addNotRetryableExceptions(FileAlreadyExistsException.class);

        return handler;
    }
}
