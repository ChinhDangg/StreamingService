package dev.chinh.streamingservice.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class MediaNameEntry {

    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @JsonProperty(ContentMetaData.LENGTH)
    private int length;

    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private Instant uploadDate;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
}
