package dev.chinh.streamingservice.filemanager.constant;

import dev.chinh.streamingservice.common.constant.MediaType;

public enum FileType {
    DIR, FILE, VIDEO, IMAGE, ALBUM, ERROR;

    public static FileType detectFileTypeFromMediaType(MediaType mediaType) {
        return mediaType == MediaType.VIDEO ? FileType.VIDEO
                : mediaType == MediaType.ALBUM ? FileType.ALBUM
                : mediaType == MediaType.IMAGE ? FileType.IMAGE
                : FileType.FILE;
    }
}
