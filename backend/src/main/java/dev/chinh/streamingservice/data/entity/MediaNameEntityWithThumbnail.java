package dev.chinh.streamingservice.data.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class MediaNameEntityWithThumbnail extends MediaNameEntity {

    @JsonProperty(ContentMetaData.THUMBNAIL)
    protected String thumbnail;
}
