package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaSearchItemResponse {

    @JsonProperty(ContentMetaData.ID)
    private String id;
    @JsonProperty(ContentMetaData.TITLE)
    private String title;
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private LocalDate uploadDate;
    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;
    @JsonProperty(ContentMetaData.WIDTH)
    private Integer width;
    @JsonProperty(ContentMetaData.HEIGHT)
    private Integer height;
}
