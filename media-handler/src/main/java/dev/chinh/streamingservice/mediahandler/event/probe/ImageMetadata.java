package dev.chinh.streamingservice.mediahandler.event.probe;

public record ImageMetadata(
        int width,
        int height,
        long size,
        String format
) implements MediaMetadataImp {}
