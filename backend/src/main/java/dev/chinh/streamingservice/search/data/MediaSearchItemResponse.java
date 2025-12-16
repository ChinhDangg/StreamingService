package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaSearchItemResponse {

    @JsonProperty(ContentMetaData.ID)
    private long id;
    @JsonProperty(ContentMetaData.TITLE)
    private String title;
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private LocalDateTime uploadDate;
    @JsonProperty(ContentMetaData.AUTHORS)
    private List<String> authors;
    private MediaType mediaType;
    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;
    @JsonProperty(ContentMetaData.WIDTH)
    private Integer width;
    @JsonProperty(ContentMetaData.HEIGHT)
    private Integer height;
}
