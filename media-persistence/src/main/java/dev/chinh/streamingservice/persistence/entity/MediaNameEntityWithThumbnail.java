package dev.chinh.streamingservice.persistence.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@MappedSuperclass
public abstract class MediaNameEntityWithThumbnail extends MediaNameEntity {

    @JsonProperty(ContentMetaData.THUMBNAIL)
    protected String thumbnail;
}
