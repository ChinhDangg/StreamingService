package dev.chinh.streamingservice.search.serve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.mediapersistence.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.search.MediaMapper;
import dev.chinh.streamingservice.search.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.search.service.*;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final ThumbnailService thumbnailService;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    private final MediaMapper mediaMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final OpenSearchSearchService searchService;
    private final MediaSearchCacheService mediaSearchCacheService;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    public record GroupSlice(
            List<Long> content,
            int page,
            int size,
            boolean hasNext
    ) {}

    public MediaDisplayContent getMediaContentInfo(String userId, long mediaId) throws Exception {
        MediaSearchItem mediaItem = getMediaSearchItem(userId, mediaId);

        MediaDisplayContent mediaDisplayContent = mediaMapper.mapDescription(mediaItem);
        if (mediaItem.hasThumbnail()) {
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                String thumbnailBucket = mediaItem.getMediaType() == MediaType.ALBUM ? mediaItem.getBucket() : ContentMetaData.THUMBNAIL_BUCKET;
                mediaDisplayContent.setThumbnail(minIOService.getObjectUrl(thumbnailBucket, ContentMetaData.removeUserIdDirFromObjectKey(userId, mediaItem.getThumbnail())));
            } else {
                mediaDisplayContent.setThumbnail(ThumbnailService.getThumbnailPath(ThumbnailService.getThumbnailUrlParentPath(), mediaId, mediaItem.getThumbnail()));
                thumbnailService.processThumbnails(userId, List.of(mediaItem));
            }
        }

        if (mediaItem.isGrouper()) {
            GroupSlice mediaIds = getNextGroupOfMedia(userId, mediaId, 0, Sort.Direction.DESC);
            mediaDisplayContent.setChildMediaIds(mediaIds);
            mediaDisplayContent.setMediaType(MediaType.GROUPER);
        } else {
            mediaDisplayContent.setMediaType(mediaItem.getMediaType());
        }
        return mediaDisplayContent;
    }

    private void addCacheGroupOfMedia(String userId, long mediaId, int page, Sort.Direction sortOrder, GroupSlice mediaIds) throws JsonProcessingException {
        String id = getCacheGroupOfMediaString(userId, mediaId);
        redisTemplate.opsForHash().put(id, page + ":" + sortOrder, objectMapper.writeValueAsString(mediaIds));
        redisTemplate.expire(id, Duration.ofMinutes(15));
    }

    public GroupSlice getCacheGroupOfMedia(String userId, long mediaId, int page, Sort.Direction sortOrder) {
        Object json = redisTemplate.opsForHash().get(getCacheGroupOfMediaString(userId, mediaId), page + ":" + sortOrder);

        if (json == null)
            return null;
        try {
            return objectMapper.readValue((String) json, GroupSlice.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached Slice<Long>", e);
        }
    }

    private String getCacheGroupOfMediaString(String userId, long mediaId) {
        return "grouper::" + userId + ":" + mediaId;
    }

    public GroupSlice getNextGroupOfMedia(String userId, long mediaId, int page, Sort.Direction sortOrder) throws JsonProcessingException {
        GroupSlice groupSlice = getCacheGroupOfMedia(userId, mediaId, page, sortOrder);
        if (groupSlice != null) {
            return groupSlice;
        }

        String lockKey = "grouper_lock:" + mediaId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    groupSlice = getCacheGroupOfMedia(userId, mediaId, page, sortOrder);
                    if (groupSlice != null) {
                        return groupSlice;
                    }

                    groupSlice = findNextGroupOfMedia(userId, mediaId, page, sortOrder);
                } finally {
                    lock.unlock();
                }
            } else {
                System.err.println("Failed to get lock for grouper media id: " + mediaId);
                groupSlice = findNextGroupOfMedia(userId, mediaId, page, sortOrder);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for lock: " + lockKey + " for media id: " + mediaId);
        }

        return groupSlice;
    }

    private GroupSlice findNextGroupOfMedia(String userId, long mediaId, int page, Sort.Direction sortOrder) throws JsonProcessingException {
        MediaSearchItem mediaItem = getMediaSearchItem(userId, mediaId);
        if (!mediaItem.isGrouper()) {
            throw new ResourceNotFoundException("No media grouper found with id: " + mediaId);
        }

        final int maxBatchSize = 20;
        Pageable pageable = PageRequest.of(page, maxBatchSize, Sort.by(sortOrder, ContentMetaData.NUM_INFO));
        Slice<Long> groupOfMedia = mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaItem.getGrouperId(), pageable);
        GroupSlice groupSlice = new GroupSlice(groupOfMedia.getContent(), page, maxBatchSize, groupOfMedia.hasNext());

        addCacheGroupOfMedia(userId, mediaId, page, sortOrder, groupSlice);
        return groupSlice;
    }

    public ResponseEntity<Void> getServePageTypeFromMedia(String userId, long mediaId) {
        MediaSearchItem mediaItem = getMediaSearchItem(userId, mediaId);

        String mediaPage = switch (mediaItem.getMediaType()) {
            case MediaType.GROUPER -> "/page/album-grouper?grouperId=" + mediaId;
            case MediaType.ALBUM -> "/page/album?mediaId=" + mediaId;
            case MediaType.VIDEO -> "/page/video?mediaId=" + mediaId;
            default -> throw new IllegalArgumentException("Unknown page type with mediaId: " + mediaId);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(mediaPage));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private MediaSearchItem getMediaSearchItem(String userId, long mediaId) {
        MediaSearchItem mediaSearchItem = mediaSearchCacheService.getCachedMediaSearchItem(userId, mediaId);
        if (mediaSearchItem != null)
            return mediaSearchItem;
        try {
            SearchResponse<MediaSearchItem> response = searchService.findById(OpenSearchService.MEDIA_INDEX_NAME, Long.parseLong(userId), mediaId, MediaSearchItem.class);
            mediaSearchItem = response.hits().hits().getFirst().source();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get media search item", e);
        }
        if (mediaSearchItem == null)
            throw new ResourceNotFoundException("No media found with id: " + mediaId);
        return mediaSearchItem;
    }

}
