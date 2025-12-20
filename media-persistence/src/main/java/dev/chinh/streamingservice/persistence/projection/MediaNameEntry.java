package dev.chinh.streamingservice.persistence.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaNameEntry {

    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @JsonProperty(ContentMetaData.LENGTH)
    private int length;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    public MediaNameEntry(String name, int length, String thumbnail) {
        this.name = name;
        this.length = length;
        this.thumbnail = thumbnail;
    }

    public MediaNameEntry(String name, int length) {
        this(name, length, null);
    }

}
