package dev.chinh.streamingservice.workers.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.chinh.streamingservice.workers.respond.RedisJobStatusListener;
import io.lettuce.core.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host}")
    private String redisHost;

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // to ignore not mapping fields
        return mapper;
    }

// user Spring default StringRedisTemplate instead
//    @Bean(name = "queueRedisTemplate")
//    public RedisTemplate<String, String> queueRedisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, String> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//
//        StringRedisSerializer stringSerializer = new StringRedisSerializer();
//
//        template.setKeySerializer(stringSerializer);
//        template.setValueSerializer(stringSerializer);
//
//        template.afterPropertiesSet();
//        return template;
//    }

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setPassword(redisPassword);
        config.setCodec(new JsonJacksonCodec());

        String redisAddress = String.format("redis://%s:%d", redisHost, redisPort);
        config.useSingleServer()
                .setAddress(redisAddress)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(12)
                .setTimeout(3000);

        return Redisson.create(config);
    }

    @Bean
    public ChannelTopic jobTopic() {
        return new ChannelTopic("job-status-channel");
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisJobStatusListener receiver) {
        return new MessageListenerAdapter(receiver, "handleJob");
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            ChannelTopic jobTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Map the adapter and its targeted method to listen strictly on our topic channel
        container.addMessageListener(listenerAdapter, jobTopic);
        return container;
    }
}
