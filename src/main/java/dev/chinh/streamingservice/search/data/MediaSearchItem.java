package dev.chinh.streamingservice.search.data;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class MediaSearchItem extends MediaSearchItemResponse {

    // Classification (will also be stored in search for fast information display)
    private String bucket;
    private String parentPath;
    private String key;  // if key exist then is an individual content item, otherwise use parentPath for grouping

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
