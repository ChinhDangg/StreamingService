package dev.chinh.streamingservice.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.Getter;
import lombok.Setter;
import org.simpleframework.xml.Default;

import java.time.Instant;

@Getter
@Setter
public class MediaNameEntry {

    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @JsonProperty(ContentMetaData.LENGTH)
    private int length;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    @Default
    public MediaNameEntry(String name, int length, String thumbnail) {
        this.name = name;
        this.length = length;
        this.thumbnail = thumbnail;
    }

    public MediaNameEntry(String name, int length) {
        this.name = name;
        this.length = length;
    }
}
