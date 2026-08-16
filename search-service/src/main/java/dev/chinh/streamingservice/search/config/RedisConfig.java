package dev.chinh.streamingservice.search.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host}")
    private String redisHost;

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

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
}
