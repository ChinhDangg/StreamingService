package dev.chinh.streamingservice.modify;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.chinh.streamingservice.data.ContentMetaData;

public enum MediaNameEntityConstant {

    AUTHORS(ContentMetaData.AUTHORS),
    CHARACTERS(ContentMetaData.CHARACTERS),
    UNIVERSES(ContentMetaData.UNIVERSES),
    TAGS(ContentMetaData.TAGS);

    private final String name;

    MediaNameEntityConstant(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() { return name; }

    @JsonCreator
    public static MediaNameEntityConstant fromValue(String v) {
        for (var e : values()) {
            if (e.name.equalsIgnoreCase(v)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid enum value: " + v);
    }
}
