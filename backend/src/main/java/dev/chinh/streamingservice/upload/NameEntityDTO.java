package dev.chinh.streamingservice.upload;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class NameEntityDTO {

    @JsonProperty(ContentMetaData.ID)
    private long id;

    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    public NameEntityDTO(long id, String name) {
        this.id = id;
        this.name = name;
    }
}
