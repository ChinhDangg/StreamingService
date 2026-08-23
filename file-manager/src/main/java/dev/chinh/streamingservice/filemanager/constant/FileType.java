package dev.chinh.streamingservice.filemanager.constant;

import dev.chinh.streamingservice.common.constant.MediaType;

public enum FileType {
    DIR, FILE, VIDEO, IMAGE, ALBUM, GROUPER;

    public static FileType detectFileTypeFromMediaType(MediaType mediaType) {
        return mediaType == MediaType.VIDEO ? FileType.VIDEO
                : mediaType == MediaType.ALBUM ? FileType.ALBUM
                : mediaType == MediaType.GROUPER ? FileType.GROUPER
                : mediaType == MediaType.IMAGE ? FileType.IMAGE
                : FileType.FILE;
    }

    public static MediaType convertFileTypeToMediaType(FileType fileType) {
        if (fileType == null)
            return null;
        return fileType == FileType.VIDEO ? MediaType.VIDEO
                : fileType == FileType.ALBUM ? MediaType.ALBUM
                : fileType == FileType.GROUPER ? MediaType.GROUPER
                : fileType == FileType.IMAGE ? MediaType.IMAGE
                : MediaType.OTHER;
    }

    public static boolean isNotDir(FileType fileType) {
        return fileType != DIR && fileType != ALBUM && fileType != GROUPER;
    }
}
