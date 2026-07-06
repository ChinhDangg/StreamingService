package dev.chinh.streamingservice.backend.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaSearchCacheService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final ObjectMapper objectMapper;

    public void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        try {
            String json = objectMapper.writeValueAsString(item);
            redisStringTemplate.opsForValue().set(id, json, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void cacheMediaSearchItems(Collection<MediaSearchItem> items) {
        List<String> keys = new ArrayList<>(items.size());
        List<String> args = new ArrayList<>(items.size());
        args.add(String.valueOf(Duration.ofMinutes(15).getSeconds()));

        items.forEach(item -> {
            keys.add("media::" + item.getId());
            try {
                args.add(objectMapper.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse json", e);
            }
        });

        DefaultRedisScript<Long> script = getCacheSearchItemsRedisScript();
        redisStringTemplate.execute(script, keys, args.toArray());
    }

    private static @NonNull DefaultRedisScript<Long> getCacheSearchItemsRedisScript() {
        String luaScript =
                "local ttl = tonumber(ARGV[1]) " +
                "for i=1, #KEYS do " +
                "   if redis.call('EXISTS', KEYS[i]) == 1 then " +
                "       redis.call('EXPIRE', KEYS[i], ttl) " +
                "   else " +
                "       redis.call('SET', KEYS[i], ARGV[i + 1], 'EX', ttl) " +
                "   end " +
                "end " +
                "return #KEYS";
        return new DefaultRedisScript<>(luaScript, Long.class);
    }

    public void cacheMediaSearchItem(MediaSearchItem item, Duration duration) {
        String id = "media::" + item.getId();
        try {
            String json = objectMapper.writeValueAsString(item);
            setOrRefreshTtl(id, json, duration.getSeconds());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void setOrRefreshTtl(String key, String value, long timeoutInSeconds) {
        String luaScript =
                "if redis.call('EXISTS', KEYS[1]) == 1 then " +
                "    return redis.call('EXPIRE', KEYS[1], ARGV[2]) " + // If exists, just update TTL
                "else " +
                "    local result = redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) " + // If not, set value + TTL
                "    if result and result.ok == 'OK' then return 1 else return 0 end " +
                "end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);

        redisStringTemplate.execute(script,
                Collections.singletonList(key),
                value,
                String.valueOf(timeoutInSeconds)
        );
    }

    public MediaSearchItem getCachedMediaSearchItem(long id) {
        String json = redisStringTemplate.opsForValue().get("media::" + id);
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, MediaSearchItem.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void removeCachedMediaSearchItem(long id) {
        redisStringTemplate.delete("media::" + id);
    }
}