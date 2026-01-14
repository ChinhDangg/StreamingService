package dev.chinh.streamingservice.mediaobject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MediaObjectEventConsumer {

    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    private final ThumbnailService thumbnailService;
    private final MediaMetaDataRepository mediaMetaDataRepository;

    private final ApplicationEventPublisher eventPublisher;

    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    private void onDeleteMediaObject(MediaUpdateEvent.MediaObjectDeleted event) throws Exception {
        System.out.println("Received media object delete event: " + event.mediaType() + " " + event.path());
        MediaType mediaType = event.mediaType();

        try {
            if (event.hasThumbnail())
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.thumbnail());
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail file: " + event.thumbnail());
            throw e;
        }

        if (mediaType == MediaType.VIDEO) {
            try {
                minIOService.removeFile(event.bucket(), event.path());
            } catch (Exception e) {
                System.err.println("Failed to delete media file: " + event.path());
                throw e;
            }
        } else if (mediaType == MediaType.ALBUM) {
            try {
                String prefix = event.path();
                // there is no filesystem in object storage, so we need to add the trailing slash to delete the exact path
                if (!prefix.endsWith("/"))
                    prefix += "/";
                Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(event.bucket(), prefix);
                for (Result<Item> result : results) {
                    String objectName = result.get().objectName();
                    if (!objectName.startsWith(prefix)) {
                        // this should not happen but for safeguard
                        logger.warn("Skipping unsafe delete: {}", objectName);
                        continue;
                    }
                    minIOService.removeFile(event.bucket(), objectName);
                }
            } catch (Exception e) {
                System.err.println("Failed to delete media files in album: " + event.path());
                throw e;
            }
        }
    }

    private void onDeleteThumbnailObject(MediaUpdateEvent.ThumbnailObjectDeleted event) throws Exception {
        System.out.println("Received thumbnail object delete event: " + event.path());
        try {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.path());
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail file: " + event.path());
            throw e;
        }
    }

    @Transactional
    public void onUpdateMediaEnrichment(MediaUpdateEvent.MediaUpdateEnrichment event) throws Exception {
        System.out.println("Received media enrichment update event: " + event.mediaId());
        try {
            MediaMetaData mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(event.mediaId()).orElseThrow(
                    () -> new IllegalArgumentException("Media not found: " + event.mediaId())
            );
            if (event.mediaType() == MediaType.VIDEO) {
                VideoMetadata videoMetadata = parseMediaMetadata(probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getPath()), VideoMetadata.class);
                mediaMetaData.setFrameRate(videoMetadata.frameRate);
                mediaMetaData.setFormat(videoMetadata.format);
                mediaMetaData.setSize(videoMetadata.size);
                mediaMetaData.setWidth(videoMetadata.width);
                mediaMetaData.setHeight(videoMetadata.height);
                mediaMetaData.setLength((int) videoMetadata.durationSeconds);
                mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getId(), mediaMetaData.getBucket(), mediaMetaData.getPath(), mediaMetaData.getLength()));
            } else if (event.mediaType() == MediaType.ALBUM) {
                var results = minIOService.getAllItemsInBucketWithPrefix(mediaMetaData.getBucket(), mediaMetaData.getPath());
                int count = 0;
                long totalSize = 0;
                String firstImage = null;
                String firstVideo = null;
                for (Result<Item> result : results) {
                    count++;
                    Item item = result.get();
                    if (MediaType.detectMediaType(item.objectName()) == MediaType.IMAGE) {
                        if (firstImage == null)
                            firstImage = item.objectName();
                        totalSize += item.size();
                    }
                    if (firstImage == null && firstVideo == null && MediaType.detectMediaType(item.objectName()) == MediaType.VIDEO) {
                        firstVideo = item.objectName();
                    }
                }
                mediaMetaData.setSize(totalSize);
                mediaMetaData.setLength(count);
                if (firstImage != null) {
                    ImageMetadata imageMetadata = parseMediaMetadata(probeMediaInfo(mediaMetaData.getBucket(), firstImage), ImageMetadata.class);
                    mediaMetaData.setWidth(imageMetadata.width);
                    mediaMetaData.setHeight(imageMetadata.height);
                    mediaMetaData.setFormat(imageMetadata.format);
                    mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getId(), mediaMetaData.getBucket(), firstImage));
                } else if (firstVideo != null) {
                    VideoMetadata videoMetadata = parseMediaMetadata(probeMediaInfo(mediaMetaData.getBucket(), firstVideo), VideoMetadata.class);
                    mediaMetaData.setWidth(videoMetadata.width);
                    mediaMetaData.setHeight(videoMetadata.height);
                    mediaMetaData.setFormat(videoMetadata.format);
                    mediaMetaData.setFrameRate(videoMetadata.frameRate);
                    mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getId(), mediaMetaData.getBucket(), firstVideo, (int) videoMetadata.durationSeconds));
                }
            } else if (event.mediaType() == MediaType.GROUPER) {
                ImageMetadata imageMetadata = parseMediaMetadata(probeMediaInfo(ContentMetaData.THUMBNAIL_BUCKET, mediaMetaData.getThumbnail()), ImageMetadata.class);
                mediaMetaData.setSize(imageMetadata.size);
                mediaMetaData.setWidth(imageMetadata.width);
                mediaMetaData.setHeight(imageMetadata.height);
                mediaMetaData.setFormat(imageMetadata.format);
            }

            // send event to update the media search index again with new metadata
            if (event.mediaSearchTopic() != null && event.mediaCreatedEvent() != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (Exception e) {
            System.err.println("Failed to update media enrichment: " + event.mediaId());
            throw e;
        }
    }

    public JsonNode probeMediaInfo(String bucket, String object) throws Exception {
        String inputUrl = minIOService.getObjectUrlForContainer(bucket, object);
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", "ffmpeg",
                "ffprobe",
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                inputUrl
        );
        //pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read all bytes asynchronously so the buffer doesn't clog. Read only standard output
        byte[] outBytes = process.getInputStream().readAllBytes();
        // Read error streaming separately
        byte[] errBytes = process.getErrorStream().readAllBytes();

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ffprobe timeout");
        }

        String output = new String(outBytes);

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorOutput = new String(errBytes);
            throw new RuntimeException("ffprobe failed — exit=" + exitCode + " output: " + errorOutput);
        }

        JsonNode jsonNode = objectMapper.readTree(output);
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
            return 0; // or throw exception
        }
    }


    @Transactional
    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_OBJECT_TOPIC, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment acknowledgment) throws Exception {
        try {
            if (event instanceof MediaUpdateEvent.MediaObjectDeleted e) {
                onDeleteMediaObject(e);
            } else if (event instanceof MediaUpdateEvent.ThumbnailObjectDeleted e) {
                onDeleteThumbnailObject(e);
            } else if (event instanceof MediaUpdateEvent.MediaUpdateEnrichment e) {
                onUpdateMediaEnrichment(e);
            } else {
                // unknown event type → log and skip
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            // throwing the exception lets DefaultErrorHandler apply retry
            throw e;
        }
    }


    // listen to DLQ and print out the event details for now
    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_OBJECT_DLQ_TOPIC,
            groupId = "media-object-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = "x-exception-message", required = false) String errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage);

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.MediaObjectDeleted e ->
                    System.out.println("Received media object delete event: " + e.mediaType() + " " + e.path());
            case MediaUpdateEvent.ThumbnailObjectDeleted e ->
                    System.out.println("Received thumbnail object delete event: " + e.path());
            case MediaUpdateEvent.MediaUpdateEnrichment e ->
                    System.out.println("Received media enrichment update event: " + e.mediaId());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge(); // ack on poison event to skip it
            }
        }
        System.out.println("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }

}
