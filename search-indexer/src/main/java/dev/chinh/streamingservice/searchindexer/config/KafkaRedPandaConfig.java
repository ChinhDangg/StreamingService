package dev.chinh.streamingservice.searchindexer.config;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // IMPORTANT: require manual ack
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
