package dev.chinh.streamingservice.data.entity;

import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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
