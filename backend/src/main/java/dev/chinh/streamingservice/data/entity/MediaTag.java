package dev.chinh.streamingservice.data.entity;

import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
