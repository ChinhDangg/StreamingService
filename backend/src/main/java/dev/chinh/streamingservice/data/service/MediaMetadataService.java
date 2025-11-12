package dev.chinh.streamingservice.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.repository.MediaAuthorRepository;
import dev.chinh.streamingservice.data.repository.MediaCharacterRepository;
import dev.chinh.streamingservice.data.repository.MediaTagRepository;
import dev.chinh.streamingservice.data.repository.MediaUniverseRepository;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaMetadataService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final MinIOService minIOService;

    public void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, Duration.ofHours(1));
    }

    public void cacheMediaSearchItem(MediaSearchItem item, Duration duration) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, duration);
    }

    public MediaSearchItem getCachedMediaSearchItem(String id) {
        return objectMapper.convertValue(redisTemplate.opsForValue().get("media::" + id), MediaSearchItem.class);
    }

    public Page<MediaNameEntry> findAllAuthors(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaAuthorRepository.findAllNames(getPageable(offset, sortBy, sortOrder)));
    }

    public Page<MediaNameEntry> findAllCharacters(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaCharacterRepository.findAllNames(getPageable(offset, sortBy, sortOrder)));
    }

    public Page<MediaNameEntry> findAllUniverses(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaUniverseRepository.findAllNames(getPageable(offset, sortBy, sortOrder)));
    }

    public Page<MediaNameEntry> findAllTags(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaTagRepository.findAllNames(getPageable(offset, sortBy, sortOrder)));
    }

    private Page<MediaNameEntry> mapInfo(Page<MediaNameEntry> entry) {
        List<MediaNameEntry> nameEntries = entry.getContent();
        nameEntries.forEach(nameEntry -> {
            try {
                nameEntry.setThumbnail(minIOService.getSignedUrlForHostNginx(
                        "thumbnail", nameEntry.getThumbnail(), 60 * 60));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return new PageImpl<>(nameEntries, PageRequest.of(entry.getNumber(), entry.getSize()), entry.getTotalElements());
    }

    private Pageable getPageable(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        final int pageSize = 20;
        return PageRequest.of(offset, pageSize, Sort.by(sortOrder, sortBy.getField()));
    }
}
