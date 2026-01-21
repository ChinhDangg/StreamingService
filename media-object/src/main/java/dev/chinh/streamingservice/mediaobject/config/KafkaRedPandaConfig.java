package dev.chinh.streamingservice.mediaobject.config;

import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import com.fasterxml.jackson.databind.JavaType;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaRedPandaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

    public static final String MEDIA_GROUP_ID = "media-object-service";

//    @Bean
//    public ConsumerFactory<String, Object> consumerFactory() {
//        Map<String, Object> props = new HashMap<>();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, MEDIA_GROUP_ID);
//        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
//
//        JsonDeserializer<Object> jsonDeserializer = new JsonDeserializer<>(Object.class);
//        jsonDeserializer.addTrustedPackages("dev.chinh.streamingservice.common.event");
//        jsonDeserializer.setUseTypeHeaders(true);
//
//        ErrorHandlingDeserializer<Object> valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
//        ErrorHandlingDeserializer<String> keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());
//
//        return new DefaultKafkaConsumerFactory<>(
//                props,
//                keyDeserializer,
//                valueDeserializer
//        );
//    }


    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, MEDIA_GROUP_ID);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Use a custom mapper that handles "ClassNotFound" generally
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper() {
            // Note: Use 'Headers' from org.apache.kafka.common.header
            @Override
            public JavaType toJavaType(Headers headers) {
                try {
                    return super.toJavaType(headers);
                } catch (Exception e) {
                    // This is the general fallback for ANY class not found or mapping error
                    return TypeFactory.defaultInstance().constructType(Map.class);
                }
            }
        };
        Map<String, Class<?>> mappings = new HashMap<>();
        mappings.put("dev.chinh.streamingservice.common.event.MediaUpdateEvent$MediaCreated", MediaUpdateEvent.MediaCreated.class);
        mappings.put("dev.chinh.streamingservice.common.event.MediaUpdateEvent$MediaDeleted", MediaUpdateEvent.MediaDeleted.class);
        mappings.put("dev.chinh.streamingservice.common.event.MediaUpdateEvent$MediaThumbnailUpdated", MediaUpdateEvent.MediaThumbnailUpdated.class);

        typeMapper.setIdClassMapping(mappings);
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);

        JsonDeserializer<Object> jsonDeserializer = new JsonDeserializer<>(Object.class);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setTypeMapper(typeMapper);
        jsonDeserializer.setUseTypeHeaders(true);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
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

        // retry + dlq handler
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }


    // for dlq only
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // No Recoverer here: just log the error and stop retrying
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(new FixedBackOff(0L, 0));

        // THIS LINE PREVENTS AUTO-COMMIT ON FAILURE
        errorHandler.setAckAfterHandle(false);

        factory.setCommonErrorHandler(errorHandler);
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

    public static final String MEDIA_OBJECT_DLQ_TOPIC = "media-object-dlq";

    @Bean
    public NewTopic mediaUpdateDlqTopic() {
        return TopicBuilder.name(MEDIA_OBJECT_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "604800000") // keep dlq messages for 7 days
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        dlqKafkaTemplate,
                        (record, ex) -> new org.apache.kafka.common.TopicPartition(
                                MEDIA_OBJECT_DLQ_TOPIC,
                                -1
                        ));

        FixedBackOff fixedBackOff = new FixedBackOff(5000L, 5);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
