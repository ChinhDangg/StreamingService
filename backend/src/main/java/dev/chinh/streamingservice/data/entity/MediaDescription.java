package dev.chinh.streamingservice.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@MappedSuperclass
public abstract class MediaDescription implements MetaDataProvider {

    @JsonProperty(ContentMetaData.ID)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @JsonProperty(ContentMetaData.TITLE)
    @Column(nullable = false)
    protected String title;

    @JsonProperty(ContentMetaData.BUCKET)
    @Column(nullable = false)
    protected String bucket;

    @JsonProperty(ContentMetaData.PARENT_PATH)
    @Column(nullable = false)
    protected String parentPath;

    @JsonProperty(ContentMetaData.KEY)
    protected String key;  // if key exist then is an individual content item, otherwise use parentPath for grouping

    @JsonProperty(ContentMetaData.THUMBNAIL)
    protected String thumbnail;

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
    protected LocalDateTime uploadDate;

    @JsonProperty(ContentMetaData.YEAR)
    @Column(nullable = false)
    protected Integer year;

    public String getPath() {
        if (parentPath != null && !parentPath.isBlank() && hasKey())
            return parentPath + "/" + key;
        else if (parentPath != null && !parentPath.isBlank())
            return parentPath;
        else if (key != null && !key.isBlank())
            return key;
        throw new RuntimeException("path and key is null");
    }

    /**
     * If media have key then it is path to an individual item.
     * Else, the path is the parent path to list of items.
     */
    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public boolean hasThumbnail() {
        return thumbnail != null && !thumbnail.isEmpty();
    }
}
