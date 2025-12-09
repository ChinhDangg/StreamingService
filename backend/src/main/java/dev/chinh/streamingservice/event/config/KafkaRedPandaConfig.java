package dev.chinh.streamingservice.event.config;

import dev.chinh.streamingservice.event.MediaUpdateEvent;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    public static final String MEDIA_GROUP_ID = "media-service";

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, MEDIA_GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "dev.chinh.streamingservice.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "true");

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
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                new JsonSerializer<>()
        );
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // to create or modify topics
    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS
        ));
    }

    public static final String mediaUpdatedOpenSearchTopic = "media-updated-opensearch-events";

    @Bean
    public NewTopic mediaUpdated() {
        return TopicBuilder.name(mediaUpdatedOpenSearchTopic)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "300_000") // delete after 5 min // if use for replay then use longer day
                .config("segment.bytes", "1_048_576")
                .build();
    }
}
