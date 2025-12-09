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
@Entity(name = ContentMetaData.CHARACTERS)
@Table(name = ContentMetaData.CHARACTERS)
public class MediaCharacter extends MediaNameEntityWithThumbnail {

    public MediaCharacter(String name, Instant uploadDate) {
        this.name = name;
        this.uploadDate = uploadDate;
    }
}