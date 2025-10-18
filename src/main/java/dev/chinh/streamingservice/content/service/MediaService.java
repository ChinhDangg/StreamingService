package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.MemoryLimitException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public abstract class MediaService {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final MinIOService minIOService;
    protected final ObjectMapper objectMapper;
    private final MediaMetaDataRepository mediaRepository;

    public boolean makeMemorySpaceForSize(long size) throws IOException, InterruptedException {
        if (OSUtil.getUsableMemory() >= size) {
            OSUtil.updateUsableMemory(-size);
            System.out.println(OSUtil.getUsableMemory());
            return true;
        }
        if (size > OSUtil.MEMORY_TOTAL)
            throw new MemoryLimitException(size / 1000, (int) OSUtil.MEMORY_TOTAL / 1000);

        long headRoom = (long) (OSUtil.MEMORY_TOTAL * 0.1);
        long neededSpace = size + headRoom;
        neededSpace = (neededSpace > OSUtil.MEMORY_TOTAL) ? size : neededSpace;

        long removingSpace = neededSpace;
        boolean enough = false;

        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<Object>> lastAccessMediaJob = getAllCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<Object> mediaJob : lastAccessMediaJob) {
            if (removingSpace <= 0) {
                enough = true;
                break;
            }

            long millisPassed = (long) (System.currentTimeMillis() - mediaJob.getScore());
            if (millisPassed < 60_000) {
                // zset is already sort, so if one found still being active - then the rest after is the same
                if (removingSpace - headRoom > 0) {
                    System.out.println("Most content is still being active, can't remove enough memory");
                    System.out.println("Serve original size from disk instead or wait");
                }
                break;
            }

            String mediaJobId = mediaJob.getValue().toString();
            String[] infoParts = mediaJobId.split(":");
            String mediaId = infoParts[0];
            MediaDescription mediaDescription = getMediaDescription(Long.parseLong(mediaId));

            removingSpace = removingSpace - mediaDescription.getSize();
            String mediaMemoryPath = String.join("/", infoParts);

            System.out.println("Removing: " + mediaJobId);
            OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);
            redisTemplate.opsForZSet().remove("cache:lastAccess", mediaJobId);
            redisTemplate.delete(mediaJobId); // delete the hash info for video or value info for album
        }
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        System.out.println(OSUtil.MEMORY_USABLE);
        return enough;
    }

    /**
     * @param mediaWorkId: specific media content saved to memory e.g. 1:p360
     */
    public void addCacheLastAccess(String mediaWorkId, Long expiry) {
        expiry = expiry == null ? System.currentTimeMillis() : expiry;
        redisTemplate.opsForZSet().add("cache:lastAccess", mediaWorkId, expiry);
    }

    public Double getCacheLastAccess(String mediaWorkId) {
        return redisTemplate.opsForZSet().score("cache:lastAccess", mediaWorkId);
    }

    /**
     * Already sorted by default. Get oldest one first
     * @return mediaJobId in batch of 50.
     */
    private Set<ZSetOperations.TypedTuple<Object>> getAllCacheLastAccess(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores("cache:lastAccess", 0, max, 0, 50);
    }

    public String getCachePartialJobId(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected MediaDescription getMediaDescription(long mediaId) {
        MediaDescription mediaDescription  = getCachedMediaSearchItem(String.valueOf(mediaId));
        if (mediaDescription == null)
            mediaDescription = findMediaMetaDataAllInfo(mediaId);
        return mediaDescription;
    }

    protected MediaSearchItem getCachedMediaSearchItem(String id) {
        return objectMapper.convertValue(redisTemplate.opsForValue().get("media::" + id), MediaSearchItem.class);
    }

    protected MediaMetaData findMediaMetaDataAllInfo(long id) {
        MediaMetaData mediaMetaData = mediaRepository.findByIdWithAllInfo(id).orElseThrow(() ->
                new IllegalArgumentException("Media not found with id " + id));
        cacheMediaSearchItem(objectMapper.convertValue(mediaMetaData, MediaSearchItem.class));
        return mediaMetaData;
    }

    private void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, Duration.ofHours(1));
    }

    protected String getFfmpegScaleString(int width, int height, int target) {
        return (width >= height) ? "scale=-2:" + target : "scale=" + target + ":-2";
    }

    protected boolean checkSrcSmallerThanTarget(int width, int height, int target) {
        if (width >= height) { // Landscape
            return height <= target;
        } else { // Portrait
            return width <= target;
        }
    }

    protected String runAndLog(String[] cmd, List<Integer> acceptableCode) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String line, lastLine = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while ((line = br.readLine()) != null) {
                //System.out.println("[ffmpeg] " + line);
                lastLine = line;
            }
        }
        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
        if (exit != 0 && (acceptableCode != null && !acceptableCode.contains(exit))) {
            throw new RuntimeException("Command failed with code " + exit);
        }
        return lastLine;
    }
}
