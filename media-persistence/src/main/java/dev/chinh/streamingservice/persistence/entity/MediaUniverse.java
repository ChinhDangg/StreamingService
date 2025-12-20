package dev.chinh.streamingservice.persistence.entity;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = ContentMetaData.UNIVERSES)
public class MediaUniverse extends MediaNameEntityWithThumbnail {

    public MediaUniverse(String name) {
        this.name = name;
    }
}
