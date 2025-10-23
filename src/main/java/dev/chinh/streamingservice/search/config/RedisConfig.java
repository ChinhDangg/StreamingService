package dev.chinh.streamingservice.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.repository.query.FluentQuery;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Key: String
        template.setKeySerializer(new StringRedisSerializer());
        // Value: JSON
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(MediaMetaDataRepository.class)
    public MediaMetaDataRepository mediaMetaDataRepository() {
        // Return a dummy instance (can be a mock or no-op)
        return new MediaMetaDataRepository() {
            @Override
            public List<MediaMetaData> findAll(Sort sort) {
                return List.of();
            }

            @Override
            public Page<MediaMetaData> findAll(Pageable pageable) {
                return null;
            }

            @Override
            public <S extends MediaMetaData> S save(S entity) {
                return null;
            }

            @Override
            public <S extends MediaMetaData> List<S> saveAll(Iterable<S> entities) {
                return List.of();
            }

            @Override
            public Optional<MediaMetaData> findById(Long aLong) {
                return Optional.empty();
            }

            @Override
            public boolean existsById(Long aLong) {
                return false;
            }

            @Override
            public List<MediaMetaData> findAll() {
                return List.of();
            }

            @Override
            public List<MediaMetaData> findAllById(Iterable<Long> longs) {
                return List.of();
            }

            @Override
            public long count() {
                return 0;
            }

            @Override
            public void deleteById(Long aLong) {

            }

            @Override
            public void delete(MediaMetaData entity) {

            }

            @Override
            public void deleteAllById(Iterable<? extends Long> longs) {

            }

            @Override
            public void deleteAll(Iterable<? extends MediaMetaData> entities) {

            }

            @Override
            public void deleteAll() {

            }

            @Override
            public void flush() {

            }

            @Override
            public <S extends MediaMetaData> S saveAndFlush(S entity) {
                return null;
            }

            @Override
            public <S extends MediaMetaData> List<S> saveAllAndFlush(Iterable<S> entities) {
                return List.of();
            }

            @Override
            public void deleteAllInBatch(Iterable<MediaMetaData> entities) {

            }

            @Override
            public void deleteAllByIdInBatch(Iterable<Long> longs) {

            }

            @Override
            public void deleteAllInBatch() {

            }

            @Override
            public MediaMetaData getOne(Long aLong) {
                return null;
            }

            @Override
            public MediaMetaData getById(Long aLong) {
                return null;
            }

            @Override
            public MediaMetaData getReferenceById(Long aLong) {
                return null;
            }

            @Override
            public <S extends MediaMetaData> Optional<S> findOne(Example<S> example) {
                return Optional.empty();
            }

            @Override
            public <S extends MediaMetaData> List<S> findAll(Example<S> example) {
                return List.of();
            }

            @Override
            public <S extends MediaMetaData> List<S> findAll(Example<S> example, Sort sort) {
                return List.of();
            }

            @Override
            public <S extends MediaMetaData> Page<S> findAll(Example<S> example, Pageable pageable) {
                return null;
            }

            @Override
            public <S extends MediaMetaData> long count(Example<S> example) {
                return 0;
            }

            @Override
            public <S extends MediaMetaData> boolean exists(Example<S> example) {
                return false;
            }

            @Override
            public <S extends MediaMetaData, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
                return null;
            }

            @Override
            public Optional<MediaMetaData> findByIdWithAllInfo(Long id) {
                return Optional.empty();
            }
            // no-op stub methods; all can return null or empty
        };
    }
}
