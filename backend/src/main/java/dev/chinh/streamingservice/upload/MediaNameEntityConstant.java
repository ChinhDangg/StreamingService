package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.Getter;

@Getter
public enum MediaNameEntityConstant {

    authors(ContentMetaData.AUTHORS),
    characters(ContentMetaData.CHARACTERS),
    universes(ContentMetaData.UNIVERSES),
    tags(ContentMetaData.TAGS);

    private final String name;

    MediaNameEntityConstant(String name) {
        this.name = name;
    }
}
