package dev.chinh.streamingservice.filemanager;

import dev.chinh.streamingservice.common.constant.MediaType;

public enum FileType {
    DIR, FILE, VIDEO, IMAGE, ALBUM, ERROR;

    public static FileType detectFileTypeFromMediaType(MediaType mediaType) {
        return mediaType == MediaType.VIDEO ? FileType.VIDEO : mediaType == MediaType.IMAGE ? FileType.IMAGE: FileType.FILE;
    }
}
