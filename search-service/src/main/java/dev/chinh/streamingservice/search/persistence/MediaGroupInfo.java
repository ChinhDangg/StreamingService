package dev.chinh.streamingservice.search.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "media_groups",
        indexes = {
                @Index(name = "idx_media_groups_grouper_id_num_info", columnList = "grouper_id, num_info"),
        }
)
public class MediaGroupInfo {

    @Setter(AccessLevel.NONE)
    @JsonProperty(ContentMetaData.ID)
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grouper_media_id", nullable = false)
    private GrouperMediaMetadata grouperMediaMetadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grouper_id")
    private MediaGroupInfo grouperInfo;

    @JsonProperty(ContentMetaData.NUM_INFO)
    private String numInfo; // using string as numInfo to have an easy sorting capability without needing to reorder all

    public Long getGrouperMediaId() {
        return grouperMediaMetadata == null ? null : grouperMediaMetadata.getId();
    }

    public Long getGrouperId() {
        return grouperInfo == null ? null : grouperInfo.getId();
    }

    public String toString() {
        return String.format("MediaGroupMetaData(id=%d, grouperMetaDataId=%d, grouperId=%d, numInfo=%s)",
                id, getGrouperMediaId(), getGrouperId(), numInfo);
    }

}
