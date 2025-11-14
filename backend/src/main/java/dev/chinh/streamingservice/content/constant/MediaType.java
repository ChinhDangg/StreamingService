package dev.chinh.streamingservice.content.constant;

import java.util.Set;

public enum MediaType {
    VIDEO,
    IMAGE,
    ALBUM,
    GROUPER,
    OTHER;

    public static MediaType detectMediaType(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return MediaType.OTHER;
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        Set<String> imageExts = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg", "heic");
        Set<String> videoExts = Set.of("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm", "m4v", "3gp");

        if (imageExts.contains(ext)) return MediaType.IMAGE;
        if (videoExts.contains(ext)) return MediaType.VIDEO;
        return MediaType.OTHER;
    }
}
