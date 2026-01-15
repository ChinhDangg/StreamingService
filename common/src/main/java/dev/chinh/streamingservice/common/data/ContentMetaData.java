package dev.chinh.streamingservice.common.data;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

import org.jetbrains.annotations.NotNull;

public class ContentMetaData {

    // Fields to use to ensure correct naming
    // Search
    public static final String TITLE = "title";
    public static final String TAGS = "tags";
    public static final String CHARACTERS = "characters";
    public static final String UNIVERSES = "universes";
    public static final String AUTHORS = "authors";
    public static final String UPLOAD_DATE = "uploadDate";
    public static final String YEAR = "year";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    // Classification
    public static final String ID = "id";
    public static final String BUCKET = "bucket";
    public static final String PARENT_PATH = "parentPath";
    public static final String KEY = "key";
    public static final String THUMBNAIL = "thumbnail";
    public static final String PREVIEW = "preview";
    public static final String LENGTH = "length";
    public static final String SIZE = "size";
    public static final String GROUP_INFO = "groupInfo";
    // Group Info
    public static final String GROUPER_ID = "grouperId";
    public static final String NUM_INFO = "numInfo";

    // Name entity
    public static final String NAME = "name";

    // Buckets
    public static final String THUMBNAIL_BUCKET = "thumbnails";
    public static final String MEDIA_BUCKET = "media";

    // Redis stream key
    public static final String FFMPEG_VIDEO_QUEUE_KEY = "ffmpeg_video_stream";


    public static void validateSearchFieldName(String fieldNameCheck) {
        if (!(fieldNameCheck.equals(TITLE) || fieldNameCheck.equals(UNIVERSES) || fieldNameCheck.equals(CHARACTERS) ||
        fieldNameCheck.equals(TAGS) || fieldNameCheck.equals(AUTHORS))) {
            throw new IllegalArgumentException("Invalid search field name: " + fieldNameCheck);
        }
    }

    public static void validateSearchRangeFieldName(String fieldNameCheck) {
        if (!(fieldNameCheck.equals(UPLOAD_DATE) || fieldNameCheck.equals(YEAR)
                || fieldNameCheck.equals(SIZE) || fieldNameCheck.equals(LENGTH))) {
            throw new IllegalArgumentException("Invalid search range field name: " + fieldNameCheck);
        }
    }

    public static void validateSearchText(String text) {
        text = text.trim();
        if (text.length() < 2) {
            throw new IllegalArgumentException("Text must be at least 2 chars: " + text);
        }
        if (text.length() > 100) {
            throw new IllegalArgumentException("Text must be at most 100 chars");
        }
    }
}
