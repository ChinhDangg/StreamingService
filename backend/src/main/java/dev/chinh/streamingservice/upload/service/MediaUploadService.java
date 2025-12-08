package dev.chinh.streamingservice.upload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.entity.MediaGroupMetaData;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.event.MediaUpdateEvent;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.serve.service.MediaDisplayService;
import dev.chinh.streamingservice.upload.MediaBasicInfo;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class MediaUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper mapper;
    private final MinIOService minIOService;
    private final ThumbnailService thumbnailService;
    private final MediaDisplayService mediaDisplayService;
    private final MediaMetadataService mediaMetadataService;

    private final ApplicationEventPublisher eventPublisher;

    private final MediaMetaDataRepository mediaRepository;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final String mediaBucket = "media";
    public static final String defaultVidPath = "vid";

    public String initiateMediaUploadRequest(String objectName, MediaType mediaType) {
        String validatedObject = validateObject(objectName, mediaType);
        if (mediaType == MediaType.VIDEO && MediaType.detectMediaType(validatedObject) != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + validatedObject);
        }
        else if (mediaType == MediaType.ALBUM && !isValidDirectoryPath(validatedObject)) {
            throw new IllegalArgumentException("Album must be a valid directory path");
        }

        return addCacheMediaUploadRequest(mediaType, validatedObject);
    }

    public String initiateMultipartUploadRequest(String sessionId, String object, MediaType mediaType) {
        var uploadRequest = getCachedMediaUploadRequest(sessionId);
        if (uploadRequest == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        validateMediaSessionInfoWithRequest(uploadRequest, validatedObject, mediaType);

        if (minIOService.objectExists(mediaBucket, validatedObject)) {
            // Personal system - so only check for name to ensure uniqueness (not content itself).
            // For wide usage - generate unique names for every object and keep all content
            // or check etag but multipart upload must ensure same part number and size.
            throw new IllegalArgumentException("Object already exists: " + validatedObject);
        }

        CreateMultipartUploadRequest multipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(multipartUploadRequest);
        String uploadId = response.uploadId();
        cacheFileUploadRequest(sessionId, uploadId, object);
        return uploadId;
    }

    public String generatePresignedPartUrl(String sessionId, String uploadId, String object, int partNumber) {
        MediaType mediaType = getCachedFileUploadRequest(sessionId, uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(builder -> builder
                .signatureDuration(Duration.ofHours(1))
                .uploadPartRequest(uploadPartRequest)
        );
        return presigned.url().toString();
    }

    public record UploadedPart(int partNumber, String etag) {}

    public void completeMultipartUpload(String sessionId, String uploadId, String object, List<UploadedPart> uploadedParts) {
        MediaType mediaType = getCachedFileUploadRequest(sessionId, uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        List<CompletedPart> completedParts = uploadedParts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber)
                        .eTag(p.etag)
                        .build()
                )
                .toList();

        CompletedMultipartUpload upload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .uploadId(uploadId)
                .multipartUpload(upload)
                .build();

        s3Client.completeMultipartUpload(completeRequest);
        removeCacheFileUploadRequest(sessionId, uploadId);
    }


    @Transactional
    public long saveGrouperMedia(MediaBasicInfo basicInfo) {
        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")));
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/grouper-not-media");
        mediaMetaData.setParentPath("grouper-not-media");
        mediaMetaData.setKey("grouper-not-media");
        mediaMetaData.setLength(0);

        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setNumInfo(0);
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaMetaData.setGroupInfo(mediaGroupInfo);

        String extension = basicInfo.getThumbnail().getOriginalFilename() == null ? ".jpg"
                : basicInfo.getThumbnail().getOriginalFilename().substring(basicInfo.getThumbnail().getOriginalFilename().lastIndexOf("."));
        String path = "grouper/" + mediaMetaData.getUploadDate() + "_" + validateObject(basicInfo.getTitle(), MediaType.ALBUM) + extension;

        try {
            minIOService.uploadFile(ThumbnailService.thumbnailBucket, path, basicInfo.getThumbnail());
            ImageMetadata imageMetadata = parseMediaMetadata(probeMediaInfo(ThumbnailService.thumbnailBucket, path), ImageMetadata.class);
            mediaMetaData.setThumbnail(path);
            mediaMetaData.setSize(imageMetadata.size);
            mediaMetaData.setWidth(imageMetadata.width);
            mediaMetaData.setHeight(imageMetadata.height);
            mediaMetaData.setFormat(imageMetadata.format);

            MediaMetaData saved = mediaRepository.save(mediaMetaData);
            long savedId = saved.getId();

            eventPublisher.publishEvent(new MediaUpdateEvent.MediaCreated(savedId, true));

            return savedId;
        } catch (Exception e) {
            try {
                minIOService.removeFile(ThumbnailService.thumbnailBucket, path);
            } catch (Exception ex) {
                throw new RuntimeException("Critical: Failed to delete thumbnail from MinIO to rollback", ex);
            }
            throw new RuntimeException("Failed to save media grouper metadata", e);
        }
    }

//    You always see your own writes in a transaction.
//    READ_COMMITTED still allows this.
//    REPEATABLE_READ also allows this.
//    SERIALIZABLE of course does.
    @Transactional
    public long saveMediaInGrouper(String sessionId, long grouperMediaId, String title) throws Exception {
        MediaUploadRequest upload = getCachedMediaUploadRequest(sessionId);
        if (upload == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        MediaMetaData grouperMedia = mediaRepository.findById(grouperMediaId).orElseThrow(
                () -> new ResourceNotFoundException("Media not found: " + grouperMediaId)
        );
        if (!grouperMedia.isGrouper()) {
            throw new IllegalArgumentException("Media is not a grouper media: " + grouperMediaId);
        }
        MediaGroupMetaData grouperGroupInfo = grouperMedia.getGroupInfo();

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(title);
        mediaMetaData.setYear(grouperMedia.getYear());
        mediaMetaData.setUploadDate(LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")));
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/" + upload.objectName);
        mediaMetaData.setParentPath(upload.objectName);

        var results = minIOService.getAllItemsInBucketWithPrefix(mediaBucket, upload.objectName);
        int count = 0;
        long totalSize = 0;
        String firstImage = null;
        for (Result<Item> result : results) {
            count++;
            Item item = result.get();
            if (MediaType.detectMediaType(result.get().objectName()) == MediaType.IMAGE) {
                if (firstImage == null)
                    firstImage = item.objectName();
                totalSize += item.size();
            }
        }
        mediaMetaData.setSize(totalSize);
        mediaMetaData.setLength(count);
        mediaMetaData.setThumbnail(firstImage);
        ImageMetadata imageMetadata = parseMediaMetadata(probeMediaInfo(mediaBucket, firstImage), ImageMetadata.class);
        mediaMetaData.setWidth(imageMetadata.width);
        mediaMetaData.setHeight(imageMetadata.height);
        mediaMetaData.setFormat(imageMetadata.format);

//        mediaRepository.incrementLength(grouperMedia.getId());
//        mediaGroupMetaDataRepository.incrementNumInfo(grouperGroupInfo.getId());

        Integer updatedNumInfo = mediaGroupMetaDataRepository.incrementNumInfoReturning(grouperGroupInfo.getId());
        Integer updatedLength = mediaRepository.incrementLengthReturning(grouperMedia.getId());

        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setGrouperMetaData(grouperGroupInfo);
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaGroupInfo.setNumInfo(updatedNumInfo);
        mediaMetaData.setGroupInfo(mediaGroupInfo);

        Long savedId = mediaRepository.save(mediaMetaData).getId();

        eventPublisher.publishEvent(new MediaUpdateEvent.LengthUpdated(grouperMedia.getId(), updatedLength));

        mediaMetadataService.removeCachedMediaSearchItem(grouperMedia.getId());
        mediaDisplayService.removeCacheGroupOfMedia(grouperMedia.getId());

        removeCacheMediaSessionRequest(sessionId);
        removeUploadSessionCacheLastAccess(sessionId);
        return savedId;
    }

    @Transactional
    public long saveMedia(String sessionId, MediaBasicInfo basicInfo) throws Exception {
        MediaUploadRequest upload = getCachedMediaUploadRequest(sessionId);
        if (upload == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")));
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/" + upload.objectName);

        if (upload.mediaType == MediaType.VIDEO) {
            int index = upload.objectName.lastIndexOf("/");
            mediaMetaData.setParentPath(upload.objectName.substring(0, index));
            mediaMetaData.setKey(upload.objectName.substring(index + 1));
            VideoMetadata videoMetadata = parseMediaMetadata(probeMediaInfo(mediaBucket, mediaMetaData.getPath()), VideoMetadata.class);
            mediaMetaData.setFrameRate(videoMetadata.frameRate);
            mediaMetaData.setFormat(videoMetadata.format);
            mediaMetaData.setSize(videoMetadata.size);
            mediaMetaData.setWidth(videoMetadata.width);
            mediaMetaData.setHeight(videoMetadata.height);
            mediaMetaData.setLength((int) videoMetadata.durationSeconds);

            mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaBucket, upload.objectName));

        } else if (upload.mediaType == MediaType.ALBUM) {
            mediaMetaData.setParentPath(upload.objectName);
            var results = minIOService.getAllItemsInBucketWithPrefix(mediaBucket, upload.objectName);
            int count = 0;
            long totalSize = 0;
            String firstImage = null;
            for (Result<Item> result : results) {
                count++;
                Item item = result.get();
                if (MediaType.detectMediaType(result.get().objectName()) == MediaType.IMAGE) {
                    if (firstImage == null)
                        firstImage = item.objectName();
                    totalSize += item.size();
                }
            }
            mediaMetaData.setSize(totalSize);
            mediaMetaData.setLength(count);
            mediaMetaData.setThumbnail(firstImage);
            ImageMetadata imageMetadata = parseMediaMetadata(probeMediaInfo(mediaBucket, firstImage), ImageMetadata.class);
            mediaMetaData.setWidth(imageMetadata.width);
            mediaMetaData.setHeight(imageMetadata.height);
            mediaMetaData.setFormat(imageMetadata.format);
        }

        MediaMetaData saved = mediaRepository.save(mediaMetaData);
        long savedId = saved.getId();

        eventPublisher.publishEvent(new MediaUpdateEvent.MediaCreated(savedId, false));

        removeCacheMediaSessionRequest(sessionId);
        removeUploadSessionCacheLastAccess(sessionId);

        return savedId;
    }

    public void removeUploadObject(String object) throws Exception {
        minIOService.removeFile(mediaBucket, object);
    }

    public JsonNode probeMediaInfo(String bucket, String object) throws Exception {
        String containerSignUrl = minIOService.getSignedUrlForContainerNginx(bucket, object, (int) Duration.ofMinutes(15).toSeconds());
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", "ffmpeg",
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                containerSignUrl
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ffprobe timeout");
        }

        String output = new String(process.getInputStream().readAllBytes());

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("ffprobe failed — exit=" + exitCode + " output: " + output);
        }

        JsonNode jsonNode = mapper.readTree(output);
        JsonNode streams = jsonNode.get("streams");
        if (streams == null || !streams.isArray()) {
            throw new RuntimeException("No stream data found in video");
        }

        return jsonNode;
    }

    public record VideoMetadata(
            short frameRate,
            String format,
            long size,
            int width,
            int height,
            double durationSeconds
    ) {}

    public record ImageMetadata(
            int width,
            int height,
            long size,
            String format
    ) {}

    public <T> T parseMediaMetadata(JsonNode jsonNode, Class<T> targetClass) {
        JsonNode streams = jsonNode.get("streams");
        JsonNode format = jsonNode.get("format");

        if (streams == null || streams.isEmpty() || format == null) {
            throw new IllegalArgumentException("Invalid ffprobe metadata");
        }

        // find any video stream (images are single-frame "video")
        JsonNode videoStream = null;
        for (JsonNode stream : streams) {
            if ("video".equals(stream.get("codec_type").asText())) {
                videoStream = stream;
                break;
            }
        }

        if (videoStream == null) {
            throw new IllegalArgumentException("No visual stream found.");
        }

        int width = videoStream.get("width").asInt();
        int height = videoStream.get("height").asInt();
        long size = format.get("size").asLong();
        String fmt = format.get("format_name").asText();

        // ---------- Returning Image ----------
        if (targetClass.equals(ImageMetadata.class)) {
            return targetClass.cast(
                    new ImageMetadata(width, height, size, fmt)
            );
        }

        // ---------- Returning Video ----------
        if (targetClass.equals(VideoMetadata.class)) {
            String frameRateStr = videoStream.get("avg_frame_rate").asText();
            short frameRate = parseRate(frameRateStr);
            double duration = format.get("duration").asDouble();

            return targetClass.cast(
                    new VideoMetadata(
                            frameRate,
                            fmt,
                            size,
                            width,
                            height,
                            duration
                    )
            );
        }

        throw new IllegalArgumentException("Unsupported metadata type: " + targetClass.getName());
    }

    private short parseRate(String rate) {
        if (rate == null || rate.isBlank() || "0/0".equals(rate)) return 0;

        String[] parts = rate.split("/");
        try {
            if (parts.length == 2) {
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                if (den == 0) return 0;

                int fps = (int) Math.round(num / den);
                return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, fps));
            }

            // fallback for plain numeric values
            int fps = (int) Math.round(Double.parseDouble(rate));
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, fps));

        } catch (NumberFormatException e) {
            return 0; // or throw exception if you prefer strict behavior
        }
    }



    private void cacheFileUploadRequest(String sessionId, String uploadId, String object) {
        redisTemplate.opsForHash().put("upload::" + sessionId, uploadId, object);
    }

    private MediaType getCachedFileUploadRequest(String sessionId, String uploadId) {
        String key = "upload::" + sessionId;
        Object cached = redisTemplate.opsForHash().get(key, uploadId);

        if (cached == null) return null;

        return MediaType.valueOf((String) redisTemplate.opsForHash().get(key, "mediaType"));
    }

    private void removeCacheFileUploadRequest(String sessionId, String uploadId) {
        String key = "upload::" + sessionId;
        redisTemplate.opsForHash().delete(key, uploadId);
    }

    public Map<Object, Object> getUploadSessionObjects(String sessionId) {
        var map = redisTemplate.opsForHash().entries("upload::" + sessionId);
        if (map.isEmpty()) return Map.of();
        map.remove("objectName");
        map.remove("mediaType");
        return map;
    }

    public record MediaUploadRequest(String objectName, MediaType mediaType) {}

    private String addCacheMediaUploadRequest(MediaType mediaType, String objectName) {
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String redisKey = "upload::" + id;

        redisTemplate.opsForHash().put(redisKey, "objectName", objectName);
        redisTemplate.opsForHash().put(redisKey, "mediaType", mediaType.name());
        addUploadSessionCacheLastAccess(id);
        return id;
    }

    public MediaUploadRequest getCachedMediaUploadRequest(String mediaUploadId) {
        String key = "upload::" + mediaUploadId;
        Object cached = redisTemplate.opsForHash().get(key, "objectName");

        if (cached == null) return null;

        return new MediaUploadRequest(
                (String) cached,
                MediaType.valueOf((String) redisTemplate.opsForHash().get(key, "mediaType"))
        );
    }

    public void removeCacheMediaSessionRequest(String sessionId) {
        String key = "upload::" + sessionId;
        redisTemplate.delete(key);
    }

    private void addUploadSessionCacheLastAccess(String sessionId) {
        redisTemplate.opsForZSet().add("upload::lastAccess", sessionId, Instant.now().toEpochMilli());
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllUploadSessionCacheLastAccess(long max) {
        return redisTemplate.opsForZSet().rangeByScoreWithScores("upload::lastAccess", 0, max, 0, 50);
    }

    public void removeUploadSessionCacheLastAccess(String sessionId) {
        redisTemplate.opsForZSet().remove("upload::lastAccess", sessionId);
    }


    private void validateMediaSessionInfoWithRequest(MediaUploadRequest request, String object, MediaType mediaType) {
        if (request.mediaType != mediaType) {
            throw new IllegalArgumentException("Mismatched media type: " + object);
        }
        if (!object.startsWith(request.objectName)) {
            throw new IllegalArgumentException("Mismatched object name: " + object);
        }
    }

    private String validateObject(String object, MediaType mediaType) {
        if (object == null) {
            throw new IllegalArgumentException("Object name must not be null");
        }

        object = object.trim();

        // Normalize separators
        object = object.replace("\\", "/");

        // Collapse multiple slashes
        object = object.replaceAll("/+", "/");

        // Remove trailing slash unless root
        if (object.endsWith("/") && object.length() > 1) {
            object = object.substring(0, object.length() - 1);
        }

        if (object.isBlank()) {
            throw new IllegalArgumentException("Object name must not be empty");
        }

        // Block directory traversal
        if (object.contains("..")) {
            throw new IllegalArgumentException("Parent directory reference '..' is not allowed");
        }

        // KEEP:
        // - Unicode letters
        // - Numbers
        // - underscore, hyphen, dot, slash
        // REMOVE everything else
        object = object.replaceAll("[^\\p{L}0-9._/-]", "");

        if (object.isBlank()) {
            throw new IllegalArgumentException("Object name is blank after sanitization");
        }

        if (mediaType == MediaType.VIDEO) {
            int pathIndex = object.lastIndexOf("/");
            if (pathIndex == -1) { // no base dir for vid, seem to vid folder - later save to user path or whatever
                object = OSUtil.normalizePath(defaultVidPath, object);
            }
        }

        return object;
    }

    private void validateObjectWithMediaType(String object, MediaType mediaType) {
        MediaType detectedType = MediaType.detectMediaType(object);
        if (detectedType == MediaType.OTHER) {
            throw new IllegalArgumentException("Invalid file type: " + object);
        }
        if (mediaType == MediaType.VIDEO && detectedType != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + object);
        }
    }

    public boolean isValidDirectoryPath(String path) {
        if (path == null) return false;

        // Trim whitespace
        path = path.trim();

        // Normalize slashes
        path = path.replace("\\", "/");

        // Cannot start or end with '/'
        if (path.startsWith("/") || path.endsWith("/")) {
            return false;
        }

        // Split into segments
        String[] segments = path.split("/");

        // Regex: allow unicode letters, digits, underscores, hyphens, spaces
        // NO filesystem-reserved characters
        String allowed = "^[\\p{L}\\p{N}_\\- ]+$";

        for (String segment : segments) {
            // Empty segments = invalid ("dir1//dir2")
            if (segment.isBlank()) {
                return false;
            }

            // Validate each directory name
            if (!segment.matches(allowed)) {
                return false;
            }
        }

        return true;
    }
}
