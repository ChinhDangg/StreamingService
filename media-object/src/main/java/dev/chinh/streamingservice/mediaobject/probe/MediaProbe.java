package dev.chinh.streamingservice.mediaobject.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.mediaobject.MinIOService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class MediaProbe {

    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;

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

    public <T extends MediaMetadata> T parseMediaMetadata(JsonNode jsonNode, Class<T> targetClass) {
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
}
