package dev.chinh.streamingservice.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media_groups")
public class MediaGroupMetaData {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaMetaData mediaMetaData;

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

    @Column(name = "grouper_id", insertable = false, updatable = false)
    private Long grouperMetaDataId;

    private Integer numInfo;    // episode/chapter for single media item or total episode/chapter for is grouper media metadata
}
