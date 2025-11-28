package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.Getter;

@Getter
public enum MediaNameEntity {

    AUTHORS(ContentMetaData.AUTHORS),
    CHARACTERS(ContentMetaData.CHARACTERS),
    UNIVERSES(ContentMetaData.UNIVERSES),
    TAGS(ContentMetaData.TAGS);

    private final String name;

    MediaNameEntity(String name) {
        this.name = name;
    }
}
