package dev.chinh.streamingservice.mediaobject.probe;

public record VideoMetadata(
        short frameRate,
        String format,
        long size,
        int width,
        int height,
        double durationSeconds
) implements MediaMetadata {}
