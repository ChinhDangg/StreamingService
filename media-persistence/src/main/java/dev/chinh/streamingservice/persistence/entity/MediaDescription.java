package dev.chinh.streamingservice.persistence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Getter
@Setter
@ToString
@MappedSuperclass
public abstract class MediaDescription implements MetaDataProvider {

    @JsonProperty(ContentMetaData.ID)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @JsonProperty(ContentMetaData.TITLE)
    @Column(nullable = false, columnDefinition = "TEXT")
    protected String title;

    @JsonProperty(ContentMetaData.BUCKET)
    protected String bucket;

    @JsonProperty(ContentMetaData.KEY)
    @Column(columnDefinition = "TEXT")
    protected String key;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    @Column(columnDefinition = "TEXT")
    protected String thumbnail;

    @JsonProperty(ContentMetaData.PREVIEW)
    @Column(columnDefinition = "TEXT")
    protected String preview;

    @JsonProperty(ContentMetaData.LENGTH)
    @Column(nullable = false)
    protected Integer length;

    @JsonProperty(ContentMetaData.SIZE)
    @Column(nullable = false)
    protected Long size;

    @JsonProperty(ContentMetaData.WIDTH)
    @Column(nullable = false)
    protected Integer width;

    @JsonProperty(ContentMetaData.HEIGHT)
    @Column(nullable = false)
    protected Integer height;

    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    @Column(nullable = false)
    protected Instant uploadDate;

    @JsonProperty(ContentMetaData.YEAR)
    @Column(nullable = false)
    protected short year;

    @JsonProperty(ContentMetaData.MEDIA_TYPE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    protected MediaType mediaType;

    public boolean hasThumbnail() {
        return thumbnail != null && !thumbnail.isEmpty();
    }
}
