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

    private String groupTitle;      // e.g.: the walking dead

    private Integer groupOrder;     // 1 (season 1)

    private String seriesTitle;     // the walking dead season 1

    private Integer chapter;        // 1 (episode 1)
}
