package dev.chinh.streamingservice.mediaupload.event.config;

import dev.chinh.streamingservice.common.event.EventTopics;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

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


    @Bean
    public KafkaAdmin.NewTopics mediaTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(EventTopics.MEDIA_ALL_TOPIC)
                        .partitions(1)
                        .replicas(1)
                        .config("retention.ms", "604800000") // delete after 7 days // if use for replay then use longer day
                        .config("segment.bytes", "100048576")
                        .build(),
                TopicBuilder.name(EventTopics.MEDIA_OBJECT_TOPIC)
                        .partitions(1)
                        .replicas(1)
                        .config("retention.ms", "604800000") // delete after 7 days // if use for replay then use longer day
                        .config("segment.bytes", "100048576")
                        .build(),
                TopicBuilder.name(EventTopics.MEDIA_SEARCH_TOPIC)
                        .partitions(1)
                        .replicas(1)
                        .config("retention.ms", "604800000")
                        .config("segment.bytes", "100048576")
                        .build(),
                TopicBuilder.name(EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC)
                        .partitions(1)
                        .replicas(1)
                        .config("retention.ms", "604800000")
                        .config("segment.bytes", "100048576")
                        .build()
        );
    }

}
