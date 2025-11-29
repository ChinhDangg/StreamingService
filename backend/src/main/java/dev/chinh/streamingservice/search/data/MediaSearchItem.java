package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescription;
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
    private List<String> tags;
    @JsonProperty(ContentMetaData.CHARACTERS)
    private List<String> characters;
    @JsonProperty(ContentMetaData.UNIVERSES)
    private List<String> universes;
    @JsonProperty(ContentMetaData.AUTHORS)
    private List<String> authors;

    @JsonProperty(ContentMetaData.GROUP_INFO)
    private MediaGroupInfo mediaGroupInfo;

    @Override
    public boolean isGrouper() {
        return mediaGroupInfo != null && (mediaGroupInfo.getGrouperId() == null || mediaGroupInfo.getGrouperId() == -1);
    }

    @Override
    public Long getGrouperId() {
        return mediaGroupInfo == null ? null : mediaGroupInfo.getGrouperId();
    }

}
