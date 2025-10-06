package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescriptor;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaSearchItem extends MediaSearchRequest implements MediaDescriptor {

    @JsonProperty(ContentMetaData.ID)
    private String id;
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private LocalDate uploadDate;

    // Classification (will also be stored in search for fast information display)
    @JsonProperty(ContentMetaData.BUCKET)
    private String bucket;
    @JsonProperty(ContentMetaData.PARENT_PATH)
    private String parentPath;
    @JsonProperty(ContentMetaData.KEY)
    private String key;  // if key exist then is an individual content item, otherwise use parentPath for grouping
    @JsonProperty(ContentMetaData.WIDTH)
    private Integer width;
    @JsonProperty(ContentMetaData.HEIGHT)
    private Integer height;

    public String getPath() {
        return parentPath + (key == null ? "" : ('/' + key));
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
