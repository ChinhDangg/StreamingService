package dev.chinh.streamingservice.search.constant;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.Getter;

@Getter
public enum SortBy {
    UPLOAD_DATE(ContentMetaData.UPLOAD_DATE),
    LENGTH(ContentMetaData.LENGTH),
    NAME(ContentMetaData.NAME),
    YEAR(ContentMetaData.YEAR),
    SIZE(ContentMetaData.SIZE);

    final String field;

    SortBy(String field) {
        this.field = field;
    }
}
