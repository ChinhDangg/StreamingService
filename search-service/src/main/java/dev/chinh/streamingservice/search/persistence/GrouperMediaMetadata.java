package dev.chinh.streamingservice.search.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "grouper_media_metadata")
public class GrouperMediaMetadata extends MediaDescription implements Persistable<Long> {

    @JsonProperty(ContentMetaData.GROUP_INFO)
    @OneToOne(mappedBy = "grouperMediaMetadata",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private MediaGroupInfo groupInfo;


    public boolean isGrouper() {
        return mediaType == MediaType.GROUPER;
    }

    public Long getGrouperId() {
        return groupInfo == null ? null : isGrouper() ? groupInfo.getId() : groupInfo.getGrouperId();
    }

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return this.isNew;
    }
}
