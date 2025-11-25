package dev.chinh.streamingservice.serve.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.serve.data.MediaDisplayContent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final ObjectMapper objectMapper;
    private final MediaMapper mediaMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final int maxBatchSize = 20;

    public MediaDisplayContent getMediaContentInfo(long mediaId) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = mediaMapper.map(mediaItem);
        if (mediaItem.hasThumbnail())
            mediaDisplayContent.setThumbnail(ThumbnailService.getThumbnailPath(mediaId, mediaItem.getThumbnail()));

        if (mediaItem.isGrouper()) {
            Slice<Long> mediaIds = getNextGroupOfMedia(mediaId, 0, Sort.Direction.DESC);
            mediaDisplayContent.setChildMediaIds(mediaIds);
            mediaDisplayContent.setMediaType(MediaType.GROUPER);
        } else {
            mediaDisplayContent.setMediaType(mediaItem.hasKey() ? MediaType.VIDEO : MediaType.ALBUM);
        }
        return mediaDisplayContent;
    }

    private void cacheGroupOfMedia(long mediaId, int offset, Slice<Long> mediaIds) {
        redisTemplate.opsForValue().set("grouper::" + mediaId + ":" + offset, mediaIds, Duration.ofMinutes(15));
    }

    public Slice<Long> getCacheGroupOfMedia(long mediaId, int offset) {
        String key = "grouper::" + mediaId + ":" + offset;
        Object json = redisTemplate.opsForValue().get(key);

        if (json == null)
            return null;
        try {
            List<Long> list = objectMapper.readValue(json.toString(), new TypeReference<>() {});
            return new SliceImpl<>(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached Slice<Long>", e);
        }
    }

    public Slice<Long> getNextGroupOfMedia(long mediaId, int offset, Sort.Direction sortOrder) {
        Slice<Long> cachedGroupOfMedia = getCacheGroupOfMedia(mediaId, offset);
        if (cachedGroupOfMedia != null) {
            return cachedGroupOfMedia;
        }

        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);
        if (!mediaItem.isGrouper()) {
            throw new ResourceNotFoundException("No media grouper found with id: " + mediaId);
        }

        Pageable pageable = PageRequest.of(offset, maxBatchSize, Sort.by(sortOrder, ContentMetaData.NUM_INFO));
        Slice<Long> groupOfMedia = mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaId, pageable);
        cacheGroupOfMedia(mediaId, offset, groupOfMedia);
        return groupOfMedia;
    }

    public ResponseEntity<Void> getServePageTypeFromMedia(long mediaId) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        String mediaPage;
        if (mediaItem.isGrouper()) {
            mediaPage = "/page/album-grouper?mediaId=" + mediaId;
        } else if (!mediaItem.hasKey()) {
            mediaPage = "/page/album?mediaId=" + mediaId;
        } else if (mediaItem.hasKey()) {
            mediaPage = "/page/video?mediaId=" + mediaId;
        } else {
            throw new IllegalStateException("Unknown page type with mediaId: " + mediaId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(mediaPage));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

}
