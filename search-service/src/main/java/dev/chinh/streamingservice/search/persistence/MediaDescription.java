package dev.chinh.streamingservice.search.persistence;

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
public abstract class MediaDescription {

    @JsonProperty(ContentMetaData.ID)
    @Id
    protected Long id;

    @JsonProperty(ContentMetaData.USER_ID)
    @Column(nullable = false)
    protected long userId;

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

    @JsonProperty(ContentMetaData.LENGTH)
    @Column(nullable = false)
    protected int length;

    @JsonProperty(ContentMetaData.SIZE)
    @Column(nullable = false)
    protected long size;

    @JsonProperty(ContentMetaData.WIDTH)
    @Column(nullable = false)
    protected int width;

    @JsonProperty(ContentMetaData.HEIGHT)
    @Column(nullable = false)
    protected int height;

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

    private short frameRate;
    private String format;

    public boolean hasThumbnail() {
        return thumbnail != null && !thumbnail.isEmpty();
    }
}
