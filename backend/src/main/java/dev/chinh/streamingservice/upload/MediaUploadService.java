package dev.chinh.streamingservice.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
    private final MediaMapper mediaMapper;

    private final MediaMetaDataRepository mediaRepository;

    private final String mediaBucket = "media";
    private final OpenSearchService openSearchService;

    public String initiateMediaUploadRequest(String objectName, MediaType mediaType) {
        String validatedObject = validateObject(objectName, mediaType);
        if (mediaType == MediaType.VIDEO && MediaType.detectMediaType(validatedObject) != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + validatedObject);
        }
        else if (mediaType == MediaType.ALBUM && !isValidDirectoryPath(validatedObject)) {
            throw new IllegalArgumentException("Album must be a valid directory path");
        }

        return cacheMediaUploadRequest(mediaType, validatedObject);
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
            removeCacheMediaUploadRequest(sessionId);
            throw new IllegalArgumentException("Object already exists: " + validatedObject);
        }

        CreateMultipartUploadRequest multipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(multipartUploadRequest);
        String uploadId = response.uploadId();
        cacheFileUploadRequest(uploadId, mediaType);
        return uploadId;
    }

    public String generatePresignedPartUrl(String object, String uploadId, int partNumber) {
        MediaType mediaType = getCachedFileUploadRequest(uploadId);
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

    public void completeMultipartUpload(String object, String uploadId, List<UploadedPart> uploadedParts) {
        MediaType mediaType = getCachedFileUploadRequest(uploadId);
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
        removeCacheFileUploadRequest(uploadId);
    }

    public record MediaBasicInfo(String title, int year) {}

    @Transactional
    public long saveMedia(String sessionId, MediaBasicInfo titleAndYear) throws Exception {
        MediaUploadRequest upload = getCachedMediaUploadRequest(sessionId);
        if (upload == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(titleAndYear.title);
        mediaMetaData.setYear(titleAndYear.year);
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

        Long savedId = null;
        try {
            MediaMetaData saved = mediaRepository.save(mediaMetaData);
            savedId = saved.getId();
            MediaSearchItem searchItem = mediaMapper.map(saved);
            openSearchService.indexDocument(OpenSearchService.INDEX_NAME, savedId, searchItem);
            removeCacheMediaUploadRequest(sessionId);
            return savedId;
        } catch (Exception e) {
            try {
                if (savedId != null)
                    openSearchService.deleteDocument(OpenSearchService.INDEX_NAME, savedId);
            } catch (IOException ie) {
                throw new RuntimeException("Critical: Failed to delete media from OpenSearch to rollback", ie);
            }
            throw new RuntimeException("Failed to save media metadata", e);
        }
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
                object = OSUtil.normalizePath("vid", object);
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

    private void cacheFileUploadRequest(String uploadId, MediaType mediaType) {
        String id = "file::" + uploadId;
        redisTemplate.opsForValue().set(id, mediaType.name(), Duration.ofHours(1));
    }

    private MediaType getCachedFileUploadRequest(String uploadId) {
        String id = "file::" + uploadId;
        Object value = redisTemplate.opsForValue().get(id);
        return value == null ? null : MediaType.valueOf((String) value);
    }

    private void removeCacheFileUploadRequest(String uploadId) {
        String id = "file::" + uploadId;
        redisTemplate.delete(id);
    }

    public record MediaUploadRequest(String objectName, MediaType mediaType) {}

    private String cacheMediaUploadRequest(MediaType mediaType, String objectName) {
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String redisKey = "upload::" + id;

        redisTemplate.opsForHash().put(redisKey, "objectName", objectName);
        redisTemplate.opsForHash().put(redisKey, "mediaType", mediaType.name());
        return id;
    }

    private MediaUploadRequest getCachedMediaUploadRequest(String mediaUploadId) {
        String key = "upload::" + mediaUploadId;
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);

        if (cached.isEmpty()) return null;

        return new MediaUploadRequest(
                (String) cached.get("objectName"),
                MediaType.valueOf((String) cached.get("mediaType"))
        );
    }

    private void removeCacheMediaUploadRequest(String mediaUploadId) {
        String key = "upload::" + mediaUploadId;
        redisTemplate.delete(key);
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
