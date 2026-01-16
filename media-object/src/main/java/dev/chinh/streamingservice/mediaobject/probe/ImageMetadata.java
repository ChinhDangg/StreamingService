package dev.chinh.streamingservice.mediaobject.probe;

public record ImageMetadata(
        int width,
        int height,
        long size,
        String format
) implements MediaMetadata {}
