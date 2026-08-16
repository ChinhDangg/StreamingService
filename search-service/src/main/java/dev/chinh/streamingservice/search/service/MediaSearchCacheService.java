package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheMediaSearchItem(String userId, MediaSearchItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForValue().set(getCacheItemString(userId, item.getId()), json, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void cacheMediaSearchItems(String userId, Collection<MediaSearchItem> items) {
        List<String> keys = new ArrayList<>(items.size());
        List<String> args = new ArrayList<>(items.size());
        args.add(String.valueOf(Duration.ofMinutes(15).getSeconds()));

        items.forEach(item -> {
            keys.add(getCacheItemString(userId, item.getId()));
            try {
                args.add(objectMapper.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse json", e);
            }
        });

        DefaultRedisScript<Long> script = getCacheSearchItemsRedisScript();
        redisTemplate.execute(script, keys, args.toArray());
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

    public void cacheMediaSearchItem(String userId, MediaSearchItem item, Duration duration) {
        try {
            String json = objectMapper.writeValueAsString(item);
            setOrRefreshTtl(getCacheItemString(userId, item.getId()), json, duration.getSeconds());
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

        redisTemplate.execute(script,
                Collections.singletonList(key),
                value,
                String.valueOf(timeoutInSeconds)
        );
    }

    public MediaSearchItem getCachedMediaSearchItem(String userId, long id) {
        String json = redisTemplate.opsForValue().get(getCacheItemString(userId, id));
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, MediaSearchItem.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    private String getCacheItemString(String userId, long id) {
        return "media::" + userId + ":" + id;
    }

    public void removeCachedMediaSearchItem(String userId, long id) {
        redisTemplate.delete(getCacheItemString(userId, id));
    }
}