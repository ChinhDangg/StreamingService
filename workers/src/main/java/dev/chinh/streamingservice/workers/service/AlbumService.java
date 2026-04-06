package dev.chinh.streamingservice.workers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.common.proto.FileItem;
import dev.chinh.streamingservice.workers.internal.FileDiscoveryService;
import io.grpc.StatusRuntimeException;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AlbumService extends MediaService implements ResourceCleanable {

    private final MemoryManager memoryManager;
    private final VideoService videoService;
    private final FileDiscoveryService fileService;

    @Value("${ffmpeg-name}")
    private String ffmpegName;

    public AlbumService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        MinIOService minIOService,
                        WorkerRedisService workerRedisService,
                        MemoryManager memoryManager,
                        VideoService videoService,
                        FileDiscoveryService fileService) {
        super(redisTemplate, objectMapper, minIOService, workerRedisService);
        this.memoryManager = memoryManager;
        this.videoService = videoService;
        this.fileService = fileService;
    }

    public record MediaUrl(MediaType type, String url) {}
    public record AlbumUrlInfo(List<MediaUrl> mediaUrlList, List<String> buckets, List<String> pathList) {}

    @Override
    public void handleJob(String tokenKey, MediaJobDescription description) {
        try {
            boolean toHandleJob = isJobWithinHandleWindow(tokenKey, description);
            if (!toHandleJob) { // check if job has passed too long or not
                workerRedisService.updateStatus(description.getWorkId(), MediaJobStatus.STOPPED.name());
                workerRedisService.releaseToken(tokenKey);
                return;
            }
            switch (description.getJobType()) {
                case "albumUrlList" -> {
                    var mediaUrlList = processAlbumList(description);
                    String mediaUrlListString = objectMapper.writeValueAsString(mediaUrlList);
                    workerRedisService.addResultToStatus(description.getWorkId(), "page::"+description.getOffset(), mediaUrlListString);
                    workerRedisService.releaseToken(tokenKey);
                }
                case "albumVideoUrl" -> {
                    String videoPartialUrl = getAlbumPartialVideoUrl(tokenKey, description);
                    workerRedisService.addResultToStatus(description.getWorkId(), "result", videoPartialUrl);
                    workerRedisService.updateStatus(description.getWorkId(), MediaJobStatus.RUNNING.name());
                }
                default -> throw new BadRequestException("Invalid jobType: " + description.getJobType());
            }
        } catch (Exception e) {
            if (description.getJobType().equals("albumUrlList"))
                workerRedisService.addResultToStatus(description.getWorkId(), "page::"+description.getOffset(), MediaJobStatus.FAILED.name());
            workerRedisService.releaseToken(tokenKey);
            throw new RuntimeException(e);
        }
    }

    private List<MediaUrl> processAlbumList(MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution resolution = mediaJobDescription.getResolution();

        boolean noFileId = false;
        Map<Object, Object> albumInfo = getCacheAlbumJobInfo(albumId);
        if (albumInfo.isEmpty()) {
            noFileId = true;
            try {
                var response = fileService.findFileByMId(mediaJobDescription.getUserId(), albumId);
                if (response.getContentCount() > 0) {
                    String fileId = response.getContent(0).getId();
                    if (fileId.isBlank())
                        throw new BadRequestException("No fileId found for albumId: " + albumId);
                    albumInfo.put("fileId", fileId);
                } else {
                    throw new BadRequestException("No file found for albumId: " + albumId);
                }
            } catch (StatusRuntimeException e) {
                System.err.println("Error fetching fileId for albumId: " + albumId);
                throw e;
            }
        }

        var response = fileService.listFiles(mediaJobDescription.getUserId(), albumInfo.get("fileId").toString(), mediaJobDescription.getOffset());

        String albumDir = "/stream/album/" + albumId + "/" + resolution.name();
        long size = 0;
        boolean hasImage = false;

        List<MediaUrl> mediaAllUrlList = new ArrayList<>();
        List<MediaUrl> mediaImageOutputList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        List<String> pathList = new ArrayList<>();
        for (FileItem f : response.getContentList()) {
            String objectName = f.getObjectName();
            String objectNameOmittedUserDir = objectName.substring(objectName.indexOf("/") + 1);

            MediaType mediaType = MediaType.detectMediaType(objectName);

            if (resolution == Resolution.original) {
                mediaAllUrlList.add(new MediaUrl(mediaType, minIOService.getObjectUrl(f.getBucket(), objectNameOmittedUserDir)));
            } else {
                if (mediaType == MediaType.IMAGE) {
                    String originalExtension = objectName.contains(".") ? objectName.substring(objectName.lastIndexOf(".") + 1)
                            .toLowerCase() : "jpg";
                    String format = (mediaJobDescription.getAcceptHeader() != null && mediaJobDescription.getAcceptHeader().contains("image/webp")) ? "webp" : originalExtension;
                    String savedFileName = objectNameOmittedUserDir + "_" + resolution + "." + format;
                    String urlPath = OSUtil.normalizePath(albumDir, savedFileName);
                    mediaAllUrlList.add(new MediaUrl(mediaType, urlPath));
                    mediaImageOutputList.add(new MediaUrl(mediaType, savedFileName));

                    bucketList.add(f.getBucket());
                    pathList.add(objectName);

                    size += f.getSize();
                    hasImage = true;
                } else if (mediaType == MediaType.VIDEO) {
                    String videoDir = "/api/album/" + albumId + "/" + resolution + "/vid/" + objectNameOmittedUserDir;
                    mediaAllUrlList.add(new MediaUrl(mediaType, videoDir));
                }
            }
        }

        if (resolution != Resolution.original && hasImage) {
            if (!memoryManager.freeMemoryForSize(size)) {
                return response.getContentList().stream()
                        .map(i ->
                                new MediaUrl(MediaType.detectMediaType(i.getObjectName()),
                                        minIOService.getObjectUrl(i.getBucket(), i.getObjectName())))
                        .toList();
            }

            AlbumUrlInfo urlInfo = new AlbumUrlInfo(mediaImageOutputList, bucketList, pathList);
            String saveDir = mediaJobDescription.getUserId() + "/" + albumId + "/" + resolution.name();
            int exitCode = processResizedImagesInBatch(urlInfo, resolution, saveDir, false);
            if (exitCode != 0) {
                System.err.println("Failed to resize images for albumId: " + albumId);
                throw new RuntimeException("Failed to resize images for albumId: " + albumId);
            }
        }

        if (noFileId || (resolution != Resolution.original && hasImage)) {
            long previousSize = albumInfo.get("size") == null ? 0 : Long.parseLong(albumInfo.get("size").toString());
            previousSize += size;
            albumInfo.put("size", String.valueOf(previousSize));
            albumInfo.putIfAbsent("width", String.valueOf(mediaJobDescription.getWidth()));
            albumInfo.putIfAbsent("height", String.valueOf(mediaJobDescription.getHeight()));
            addCacheAlbumJobInfo(albumId, albumInfo);
        }

        return mediaAllUrlList;
    }

    private int processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, Resolution resolution, String saveDir, boolean isAlbum) throws InterruptedException, IOException {
        String[] shellCmd;
        if (ffmpegName == null || ffmpegName.isEmpty()) {
            // PROD: Start a local bash session
            shellCmd = new String[]{"sh"};
        } else {
            // DEV: Start a bash session inside the ffmpeg container
            shellCmd = new String[]{"docker", "exec", "-i", ffmpegName, "sh"};
        }
        // Start one persistent bash session inside ffmpeg container
        ProcessBuilder pb = new ProcessBuilder(shellCmd).redirectErrorStream(true);
        Process process = pb.start();

        // Setup thread-safe list to hold logs
        List<String> logs = Collections.synchronizedList(new ArrayList<>());
        Thread logConsumer = Thread.ofVirtual().unstarted(() -> getLogsFromInputStream(logs, process.getInputStream()));
        logConsumer.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

            OSUtil.createTempDir(saveDir, ffmpegName);

            String scale = getFfmpegScaleString(resolution, true);
            for (int i = 0; i < albumUrlInfo.mediaUrlList.size(); i++) {
                String output = albumUrlInfo.mediaUrlList.get(i).url;
                output = "/chunks/" + saveDir + "/" + output;

                String bucket = isAlbum ? albumUrlInfo.buckets.getFirst() : albumUrlInfo.buckets.get(i);
                String input = minIOService.getObjectUrlForContainer(bucket, albumUrlInfo.pathList.get(i));

                String ffmpegCmd = String.format(
                        "ffmpeg -n -hide_banner -loglevel info " +
                                "-i \"%s\" -vf %s -q:v 2 -frames:v 1 \"%s\"",
                        input, scale, output
                );
                writer.write(ffmpegCmd + "\n");
                writer.flush();
            }

            // close stdin to end the bash session
            writer.write("exit\n");
            writer.flush();
        } catch (Exception e) {
            process.destroy();
            System.err.println("Error processing resized images: " + e.getMessage());
        }

        int exit = process.waitFor();
        System.out.println("ffmpeg Resizing Images exited with code " + exit);
        if (exit != 0) {
            logs.forEach(System.out::println);
        }
        return exit;
    }

    public String getAlbumVidCacheJobIdString(long albumId, String objectName, Resolution resolution) {
        return albumId + ":" + resolution + ":" + objectName;
    }

    private String getAlbumPartialVideoUrl(String tokenKey, MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution albumRes = mediaJobDescription.getResolution();
        String objectName = mediaJobDescription.getUserId() + "/" + mediaJobDescription.getKey();
        String objectNameOmittedUserDir = mediaJobDescription.getKey();
        Resolution res = mediaJobDescription.getVidResolution();

        final String albumVidCacheJobId = getAlbumVidCacheJobIdString(albumId, objectNameOmittedUserDir, res);

        if (MediaType.detectMediaType(objectName) != MediaType.VIDEO)
            throw new BadRequestException("Invalid video path: " + objectName);

        String bucket = ContentMetaData.VIDEO_BUCKET;

        if (res == Resolution.original) {
            return minIOService.getObjectUrl(bucket, objectNameOmittedUserDir);
        }

        List<String> streamUrlPaths = List.of(
                "album-vid",
                String.valueOf(albumId),
                albumRes.name(),
                objectNameOmittedUserDir,
                res.name()
        );
        String streamUrl = "/stream/" + (String.join("/", streamUrlPaths)) + masterFileName;

        MediaJobStatus mediaJobStatus = videoService.getVideoJobStatus(albumVidCacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (!mediaJobStatus.equals(MediaJobStatus.STOPPED))
                return streamUrl;
            else
                prevJobStopped = true;
        }

        long objectSize = minIOService.getObjectSize(bucket, objectName);
        boolean enoughSpace = memoryManager.freeMemoryForSize(objectSize);
        if (!enoughSpace)
            return minIOService.getObjectUrl(bucket, objectNameOmittedUserDir);

        final String videoDir = albumId + "/" + objectNameOmittedUserDir + "/" + res.name();
        String userDir = mediaJobDescription.getUserId() + "/" + videoDir;
        OSUtil.createTempDir(userDir, ffmpegName);
        String containerDir = "/chunks/" + userDir;
        String outPath = containerDir + masterFileName;

        String scale = getFfmpegScaleString(res, false);
        String input = minIOService.getObjectUrlForContainer(bucket, objectName);
        String partialVideoJobId = videoService.createPartialVideo(tokenKey, input, scale, videoDir, outPath, prevJobStopped, albumVidCacheJobId);

        videoService.addCacheVideoJobStatus(albumVidCacheJobId, partialVideoJobId, objectSize, MediaJobStatus.RUNNING);
        videoService.addCacheRunningJob(albumVidCacheJobId);

        videoService.checkPlaylistCreated(userDir + masterFileName);
        return streamUrl;
    }

    private String getFfmpegScaleString(Resolution resolution, boolean wrapInDoubleQuotes) {
        if (wrapInDoubleQuotes)
            return String.format(
                    "\"scale='if(gte(iw,ih),-2,min(iw,%1$d))':'if(gte(ih,iw),-2,min(ih,%1$d))'\"",
                    resolution.getResolution()
            );
        return String.format(
                "scale='if(gte(iw,ih),-2,min(iw,%1$d))':'if(gte(ih,iw),-2,min(ih,%1$d))'",
                resolution.getResolution()
        );
    }


    private String getAlbumCacheHashIdString(long albumId) {
        return albumId + ":album";
    }

    private final String albumLastAccessKey = "cache:lastAccess:album";

    public void removeAlbumCacheLastAccess(String albumJobId) {
        removeCacheLastAccess(albumLastAccessKey, albumJobId);
    }

    public Set<ZSetOperations.TypedTuple<String>> getAllAlbumCacheLastAccess(long max) {
        return getAllCacheLastAccess(albumLastAccessKey, max);
    }

    private void addCacheAlbumJobInfo(long albumId, Map<Object, Object> albumInfo) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        redisTemplate.opsForHash().putAll(albumHashId, albumInfo);
        redisTemplate.expire(albumHashId, Duration.ofHours(1).toMillis(), TimeUnit.MILLISECONDS);
    }

    private Map<Object, Object> getCacheAlbumJobInfo(long albumId) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        return redisTemplate.opsForHash().entries(albumHashId);
    }

    /**
     * Only free album image info. Videos in album is handled by video service free memory
     */
    @Override
    public boolean freeMemorySpaceForSize(long size) {
        if (OSUtil.getUsableMemory() >= size) {
            OSUtil.updateUsableMemory(-size);
            return true;
        }
        if (size > OSUtil.MEMORY_TOTAL) {
            System.out.println("Memory limit reached: " + size / 1000 + " MB");
            return false;
        }

        long headRoom = (long) (OSUtil.MEMORY_TOTAL * 0.1);
        long neededSpace = size + headRoom - OSUtil.getUsableMemory();
        neededSpace = (neededSpace > OSUtil.MEMORY_TOTAL) ? size : neededSpace;

        long removingSpace = neededSpace;
        boolean enough = false;

        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessMediaJob = getAllAlbumCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> mediaJob : lastAccessMediaJob) {
            if (removingSpace <= 0) {
                enough = true;
                break;
            }

            long millisPassed = (long) (now - mediaJob.getScore());
            if (millisPassed < 60_000) {
                // zset is already sort, so if one found still being active - then the rest after is the same
                if (removingSpace - headRoom > 0) {
                    System.out.println("Most content is still being active, can't remove enough memory");
                    System.out.println("Serve original size from disk instead or wait");
                }
                break;
            }

            String mediaJobId = Objects.requireNonNull(mediaJob.getValue());
            System.out.println("Removing: " + mediaJobId);

            String[] mediaJobIdParts = mediaJobId.split(":");
            long albumId = Long.parseLong(mediaJobIdParts[0]);
            Resolution resolution = Resolution.valueOf(mediaJobIdParts[1]);

            // avoid removing entire album info (pathList, bucket, size) in making space
            // (only remove the album job which actually has memory data usage)
            if (mediaJobId.endsWith(":album")) {
                continue;
            }

            try {
                Map<Object, Object> albumInfo = getCacheAlbumJobInfo(albumId);
                long estimatedSize = albumInfo.get("size") == null ? 0 : Long.parseLong(albumInfo.get("size").toString());
                estimatedSize = Resolution.getEstimatedSize(
                        estimatedSize,
                        Integer.parseInt(albumInfo.getOrDefault("width", 1).toString()),
                        Integer.parseInt(albumInfo.getOrDefault("height", 1).toString()),
                        resolution);
                System.out.println("Estimated size: " + estimatedSize);

                String mediaMemoryPath = mediaJobId.replace(":", "/");
                boolean deleted = OSUtil.deleteForceMemoryDirectory(mediaMemoryPath, ffmpegName);
                if (deleted)
                    removingSpace -= estimatedSize;
            } catch (IOException e) {
                System.err.println("Failed to delete memory path: " + mediaJobId.replace(":", "/"));
            } catch (Exception e) {
                System.out.println("Failed to get estimated size to estimate removing space");
            }

            removeAlbumCacheLastAccess(mediaJobId);
            removeJobStatus(mediaJobId);
        }
        if (!enough && removingSpace - headRoom <= 0)
            enough = true;
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        return enough;
    }
}
