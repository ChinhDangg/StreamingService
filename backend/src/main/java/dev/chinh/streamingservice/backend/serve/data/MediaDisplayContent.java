package dev.chinh.streamingservice.backend.serve.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.backend.serve.service.MediaDisplayService;
import dev.chinh.streamingservice.persistence.projection.MediaNameSearchItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaDisplayContent {

    @JsonProperty(ContentMetaData.ID)
    private Long id;

    @JsonProperty(ContentMetaData.TITLE)
    private String title;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    @JsonProperty(ContentMetaData.TAGS)
    private List<MediaNameSearchItem> tags;

    @JsonProperty(ContentMetaData.CHARACTERS)
    private List<MediaNameSearchItem> characters;

    @JsonProperty(ContentMetaData.UNIVERSES)
    private List<MediaNameSearchItem> universes;

    @JsonProperty(ContentMetaData.AUTHORS)
    private List<MediaNameSearchItem> authors;

    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;

    @JsonProperty(ContentMetaData.SIZE)
    private Long size;

    @JsonProperty(ContentMetaData.WIDTH)
    private Integer width;

    @JsonProperty(ContentMetaData.HEIGHT)
    private Integer height;

    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private Instant uploadDate;

    @JsonProperty(ContentMetaData.YEAR)
    private Integer year;

    private MediaType mediaType;

    // optional inner child media ids if the current media is just a grouper (not individual video item or album)
    MediaDisplayService.GroupSlice childMediaIds;
}
