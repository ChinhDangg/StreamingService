package dev.chinh.streamingservice.serve.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.serve.service.MediaDisplayService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
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
    private List<String> tags;

    @JsonProperty(ContentMetaData.CHARACTERS)
    private List<String> characters;

    @JsonProperty(ContentMetaData.UNIVERSES)
    private List<String> universes;

    @JsonProperty(ContentMetaData.AUTHORS)
    private List<String> authors;

    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;

    @JsonProperty(ContentMetaData.SIZE)
    private Long size;

    @JsonProperty(ContentMetaData.WIDTH)
    private Integer width;

    @JsonProperty(ContentMetaData.HEIGHT)
    private Integer height;

    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private LocalDateTime uploadDate;

    @JsonProperty(ContentMetaData.YEAR)
    private Integer year;

    private MediaType mediaType;

    // optional inner child media ids if the current media is just a grouper (not individual video item or album)
    MediaDisplayService.GroupSlice childMediaIds;
}
