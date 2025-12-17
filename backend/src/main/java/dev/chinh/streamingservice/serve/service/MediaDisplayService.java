package dev.chinh.streamingservice.serve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.serve.data.MediaDisplayContent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final AlbumService albumService;
    private final ThumbnailService thumbnailService;
    private final ObjectMapper objectMapper;
    private final MediaMapper mediaMapper;
    private final RedisTemplate<String, String> redisStringTemplate;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final int maxBatchSize = 20;

    public record GroupSlice(
            List<Long> content,
            int page,
            int size,
            boolean hasNext
    ){}

    public MediaDisplayContent getMediaContentInfo(long mediaId) throws JsonProcessingException {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = mediaMapper.map(mediaItem);
        if (mediaItem.hasThumbnail()) {
            mediaDisplayContent.setThumbnail(ThumbnailService.getThumbnailPath(mediaId, mediaItem.getThumbnail()));
            thumbnailService.processThumbnails(List.of(mediaItem));
        }

        if (mediaItem.isGrouper()) {
            GroupSlice mediaIds = getNextGroupOfMedia(mediaId, 0, Sort.Direction.DESC);
            mediaDisplayContent.setChildMediaIds(mediaIds);
            mediaDisplayContent.setMediaType(MediaType.GROUPER);
        } else {
            mediaDisplayContent.setMediaType(mediaItem.hasKey() ? MediaType.VIDEO : MediaType.ALBUM);
        }
        return mediaDisplayContent;
    }

    private void addCacheGroupOfMedia(long mediaId, int offset, Sort.Direction sortOrder, GroupSlice mediaIds) throws JsonProcessingException {
        String id = "grouper::" + mediaId;
        redisStringTemplate.opsForHash().put(id, offset + ":" + sortOrder, objectMapper.writeValueAsString(mediaIds));
        redisStringTemplate.expire(id, Duration.ofMinutes(15));
    }

    public GroupSlice getCacheGroupOfMedia(long mediaId, int offset, Sort.Direction sortOrder) {
        String id = "grouper::" + mediaId;
        Object json = redisStringTemplate.opsForHash().get(id, offset + ":" + sortOrder);

        if (json == null)
            return null;
        try {
            return objectMapper.readValue((String) json, GroupSlice.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached Slice<Long>", e);
        }
    }

    public void removeCacheGroupOfMedia(long mediaId) {
        String id = "grouper::" + mediaId;
        redisStringTemplate.delete(id);
    }

    public void deleteAllCacheForMedia(long mediaId) {
        String pattern = "grouper::" + mediaId + ":*";

        // 1. Use the non-deprecated redisTemplate.scan() method
        //    It respects the configured KeySerializer (StringRedisSerializer) and returns String keys.
        try (Cursor<String> cursor = redisStringTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build())) {

            // 2. Collect all keys returned by the cursor into a list
            List<String> keysToDelete = new ArrayList<>();
            cursor.forEachRemaining(keysToDelete::add);

            // 3. Delete the keys in a single operation
            if (!keysToDelete.isEmpty()) {
                // redisTemplate.delete(Collection<K> keys) is safe and respects serialization
                redisStringTemplate.delete(keysToDelete);
            }
        }
        // The try-with-resources block ensures the cursor is closed automatically.
    }

    public GroupSlice getNextGroupOfMedia(long mediaId, int offset, Sort.Direction sortOrder) throws JsonProcessingException {
        GroupSlice cachedGroupOfMedia = getCacheGroupOfMedia(mediaId, offset, sortOrder);
        if (cachedGroupOfMedia != null) {
            return cachedGroupOfMedia;
        }

        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);
        if (!mediaItem.isGrouper()) {
            throw new ResourceNotFoundException("No media grouper found with id: " + mediaId);
        }

        Pageable pageable = PageRequest.of(offset, maxBatchSize, Sort.by(sortOrder, ContentMetaData.NUM_INFO));
        Slice<Long> groupOfMedia = mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaItem.getGrouperId(), pageable);
        GroupSlice groupSlice = new GroupSlice(groupOfMedia.getContent(), offset, maxBatchSize, groupOfMedia.hasNext());

        addCacheGroupOfMedia(mediaId, offset, sortOrder, groupSlice);
        return groupSlice;
    }

    public ResponseEntity<Void> getServePageTypeFromMedia(long mediaId) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        String mediaPage;
        if (mediaItem.isGrouper()) {
            mediaPage = "/page/album-grouper?grouperId=" + mediaId;
        } else if (!mediaItem.hasKey()) {
            mediaPage = "/page/album?mediaId=" + mediaId;
        } else if (mediaItem.hasKey()) {
            mediaPage = "/page/video?mediaId=" + mediaId;
        } else {
            throw new IllegalArgumentException("Unknown page type with mediaId: " + mediaId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(mediaPage));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

}
