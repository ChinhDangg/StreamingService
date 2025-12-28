package dev.chinh.streamingservice.persistence.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaGroupInfo {

    @JsonProperty(ContentMetaData.ID)
    private Long id;

    @JsonProperty(ContentMetaData.GROUPER_ID)
    private Long grouperId;

    @JsonProperty(ContentMetaData.NUM_INFO)
    private Integer numInfo;

    public MediaGroupInfo(Long id, Long grouperId) {
        this.id = id;
        this.grouperId = grouperId;
    }
}
