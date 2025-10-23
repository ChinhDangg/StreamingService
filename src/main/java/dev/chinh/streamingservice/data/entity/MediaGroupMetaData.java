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

    // is group means not an individual item, that holds shared info and group other Media MetaData
    private boolean isGroup;

    private Integer numInfo;    // episode/chapter for single media item or total episode/chapter for is group media
}
