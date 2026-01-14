package dev.chinh.streamingservice.persistence.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MediaNameSearchItem {

    @JsonProperty(ContentMetaData.ID)
    private long id;

    @JsonProperty(ContentMetaData.NAME)
    private String name;
}