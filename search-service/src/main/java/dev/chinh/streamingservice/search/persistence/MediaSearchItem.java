package dev.chinh.streamingservice.search.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.search.data.MediaSearchGroupInfo;
import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaSearchItem extends MediaDescription {

    @JsonProperty(ContentMetaData.TAGS)
    private List<MediaNameSearchItem> tags;
    @JsonProperty(ContentMetaData.CHARACTERS)
    private List<MediaNameSearchItem> characters;
    @JsonProperty(ContentMetaData.UNIVERSES)
    private List<MediaNameSearchItem> universes;
    @JsonProperty(ContentMetaData.AUTHORS)
    private List<MediaNameSearchItem> authors;

    @JsonProperty(ContentMetaData.GROUP_INFO)
    private MediaSearchGroupInfo groupInfo;

    public boolean isGrouper() {
        return mediaType == MediaType.GROUPER;
    }

    public Long getGrouperId() {
        return groupInfo == null ? null : isGrouper() ? groupInfo.getId() : groupInfo.getGrouperId();
    }
}
