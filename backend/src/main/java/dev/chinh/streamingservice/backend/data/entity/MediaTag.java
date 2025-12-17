package dev.chinh.streamingservice.backend.data.entity;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = ContentMetaData.TAGS)
public class MediaTag extends MediaNameEntity {

    public MediaTag(String name) {
        this.name = name;
    }
}
