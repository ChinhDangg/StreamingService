package dev.chinh.streamingservice.filemanager.constant;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.Getter;

@Getter
public enum SortBy {
    UPLOAD(ContentMetaData.UPLOAD_DATE),
    NAME(ContentMetaData.NAME),
    SIZE(ContentMetaData.SIZE),
    LENGTH(ContentMetaData.LENGTH),
    RESOLUTION(ContentMetaData.RESOLUTION + "." + ContentMetaData.AREA);

    private final String field;

    SortBy(String field) {
        this.field = field;
    }
}
