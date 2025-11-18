package dev.chinh.streamingservice.data.service;

import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlbumService albumService;

    public static final Resolution thumbnailResolution = Resolution.p360;

    public void processThumbnails(List<MediaNameEntry> items) {
        long now = System.currentTimeMillis() + 60 * 60 * 1000;
        List<MediaNameEntry> newThumbnails = new ArrayList<>();
        for (MediaNameEntry item : items) {
            if (item.getThumbnail() == null)
                continue;
            String thumbnailFileName = Paths.get(getThumbnailPath(item.getName(), item.getThumbnail())).getFileName().toString();
            if (!hasCacheThumbnails(thumbnailFileName)) {
                newThumbnails.add(item);
            }
        }
        if (newThumbnails.isEmpty())
            return;
        var albumUrlInfo = getThumbnailImagesAsAlbumUrls(newThumbnails);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = albumService.processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), true);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            for (MediaNameEntry item : newThumbnails) {
                addCacheThumbnails(Paths.get(getThumbnailPath(item.getName(), item.getThumbnail())).getFileName().toString(), now);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void processThumbnails(Collection<MediaSearchItem> items) {
        List<MediaDescription> newThumbnails = new ArrayList<>();
        for (MediaDescription item : items) {
            if (!item.hasThumbnail())
                continue;
            String thumbnailFileName = Paths.get(getThumbnailPath(item.getId(), item.getThumbnail())).getFileName().toString();
            if (!hasCacheThumbnails(thumbnailFileName)) {
                newThumbnails.add(item);
            }
        }
        if (newThumbnails.isEmpty())
            return;
        var albumUrlInfo = getMixThumbnailImagesAsAlbumUrls(newThumbnails);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = albumService.processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), false);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            long now = System.currentTimeMillis() + 60 * 60 * 1000;
            for (MediaDescription item : newThumbnails) {
                addCacheThumbnails(Paths.get(getThumbnailPath(item.getId(), item.getThumbnail())).getFileName().toString(), now);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AlbumService.AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList) {
        List<String> pathList = new ArrayList<>();
        List<AlbumService.MediaUrl> albumUrlList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            bucketList.add(mediaDescription.getBucket());
            pathList.add(mediaDescription.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaDescription.getId(), mediaDescription.getThumbnail());
            albumUrlList.add(new AlbumService.MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumService.AlbumUrlInfo(albumUrlList, bucketList, pathList);
    }

    public AlbumService.AlbumUrlInfo getThumbnailImagesAsAlbumUrls(List<MediaNameEntry> mediaNameEntryList) {
        List<String> pathList = new ArrayList<>();
        List<AlbumService.MediaUrl> albumUrlList = new ArrayList<>();
        for (MediaNameEntry mediaNameEntry : mediaNameEntryList) {
            if (mediaNameEntry.getThumbnail() == null)
                continue;

            pathList.add(mediaNameEntry.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaNameEntry.getName(), mediaNameEntry.getThumbnail());
            albumUrlList.add(new AlbumService.MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumService.AlbumUrlInfo(albumUrlList, List.of("thumbnail"), pathList);
    }

    private void addCacheThumbnails(String thumbnailFileName, long expiry) {
        redisTemplate.opsForZSet().add("thumbnail-cache", thumbnailFileName, expiry);
    }

    private boolean hasCacheThumbnails(String thumbnailFileName) {
        return redisTemplate.opsForZSet().score("thumbnail-cache", thumbnailFileName) != null;
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllThumbnailCacheLastAccess(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        redisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
    }

    public static String getThumbnailPath(String id, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + id + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailPath(long mediaId, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + mediaId + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }

}
