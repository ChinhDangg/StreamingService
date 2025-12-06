package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.search.data.MediaGroupInfo;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

@RequiredArgsConstructor
public abstract class MediaService {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final MediaMapper mediaMapper;
    private final MediaMetaDataRepository mediaRepository;
    protected final MinIOService minIOService;
    protected final MediaMetadataService mediaMetadataService;

    protected final String masterFileName = "/master.m3u8";

    /**
     * @param mediaWorkId: specific media content saved to memory e.g. 1:p360
     */
    protected void addCacheLastAccess(String key, String mediaWorkId, Long expiry) {
        expiry = expiry == null ? System.currentTimeMillis() : expiry;
        redisTemplate.opsForZSet().add(key, mediaWorkId, expiry);
    }

    protected Double getCacheLastAccess(String key, String mediaWorkId) {
        return redisTemplate.opsForZSet().score(key, mediaWorkId);
    }

    protected void removeCacheLastAccess(String key, String mediaWorkId) {
        redisTemplate.opsForZSet().remove(key, mediaWorkId);
    }

    /**
     * Already sorted by default. Get oldest one first
     * @return mediaJobId in batch of 50.
     */
    protected Set<ZSetOperations.TypedTuple<Object>> getAllCacheLastAccess(String key, long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, 0, max, 0, 50);
    }

    public String getCacheMediaJobId(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected String getNginxVideoStreamUrl(String videoDir) {
        return "/stream/" + videoDir + masterFileName;
    }

    protected MediaDescription getMediaDescription(long mediaId) {
        MediaDescription mediaDescription = mediaMetadataService.getCachedMediaSearchItem(mediaId);
        if (mediaDescription == null)
            mediaDescription = findMediaMetaDataAllInfo(mediaId);
        return mediaDescription;
    }

    protected MediaMetaData findMediaMetaDataAllInfo(long id) {
        MediaMetaData mediaMetaData = mediaRepository.findByIdWithAllInfo(id).orElseThrow(() ->
                new IllegalArgumentException("Media not found with id " + id));
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData);
        if (mediaMetaData.getGrouperId() != null || mediaMetaData.isGrouper()) {
            mediaSearchItem.setMediaGroupInfo(
                    new MediaGroupInfo(mediaMetaData.getGrouperId(), mediaMetaData.getGroupInfo().getNumInfo()));
        }
        mediaMetadataService.cacheMediaSearchItem(mediaSearchItem);
        return mediaMetaData;
    }

    protected boolean checkSrcSmallerThanTarget(int width, int height, int target) {
        if (width >= height) { // Landscape
            return height <= target;
        } else { // Portrait
            return width <= target;
        }
    }
}
