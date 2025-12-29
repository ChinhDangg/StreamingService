package dev.chinh.streamingservice.backend.serve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.backend.content.service.AlbumService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.backend.content.service.ThumbnailService;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.backend.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.repository.MediaGroupMetaDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final AlbumService albumService;
    private final ThumbnailService thumbnailService;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    private final MediaMapper mediaMapper;
    private final RedisTemplate<String, String> redisStringTemplate;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final int maxBatchSize = 20;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    public record GroupSlice(
            List<Long> content,
            int page,
            int size,
            boolean hasNext
    ){}

    public MediaDisplayContent getMediaContentInfo(long mediaId) throws Exception {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = mediaMapper.map(mediaItem);
        if (mediaItem.hasThumbnail()) {
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                String thumbnailBucket = mediaItem.getMediaType() == MediaType.ALBUM ? mediaItem.getBucket() : ContentMetaData.THUMBNAIL_BUCKET;
                mediaDisplayContent.setThumbnail(minIOService.getSignedUrlForHostNginx(thumbnailBucket, mediaItem.getThumbnail(), 60 * 60));
            } else {
                mediaDisplayContent.setThumbnail(ThumbnailService.getThumbnailPath(mediaId, mediaItem.getThumbnail()));
                thumbnailService.processThumbnails(List.of(mediaItem));
            }
        }

        if (mediaItem.isGrouper()) {
            GroupSlice mediaIds = getNextGroupOfMedia(mediaId, 0, Sort.Direction.DESC);
            mediaDisplayContent.setChildMediaIds(mediaIds);
            mediaDisplayContent.setMediaType(MediaType.GROUPER);
        } else {
            mediaDisplayContent.setMediaType(mediaItem.getMediaType());
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

}
