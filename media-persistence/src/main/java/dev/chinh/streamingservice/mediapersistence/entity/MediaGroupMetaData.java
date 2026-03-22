package dev.chinh.streamingservice.mediapersistence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "media_groups",
        indexes = {
                @Index(name = "idx_media_groups_grouper_id_num_info", columnList = "grouper_id, num_info"),
        }
)
public class MediaGroupMetaData {

    @Setter(AccessLevel.NONE)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaMetaData mediaMetaData;

    @Setter(AccessLevel.NONE)
    // Read-only access to foreign key column
    @Column(name = "media_id", insertable = false, updatable = false)
    private Long mediaMetaDataId;

    // If has media group id then the media is an individual item that hold the grouper id.
    // Media Grouper holds shared info and group other Media MetaData
    // does not hold bucket, parentPath, and key info, and does not hold another media group metadata
    // Individual media will share same grouper id to group together.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grouper_id")
    private MediaGroupMetaData grouperMetaData;

    @Setter(AccessLevel.NONE)
    @JsonProperty(ContentMetaData.GROUPER_ID)
    @Column(name = "grouper_id", insertable = false, updatable = false)
    private Long grouperMetaDataId;

    @JsonProperty(ContentMetaData.NUM_INFO)
    private String numInfo; // using string as numInfo to have an easy sorting capability without needing to reorder all
}
