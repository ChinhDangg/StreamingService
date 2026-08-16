package dev.chinh.streamingservice.mediahandler.event.probe;

public record VideoMetadata(
        short frameRate,
        String format,
        long size,
        int width,
        int height,
        double durationSeconds
) implements MediaMetadataImp {}
