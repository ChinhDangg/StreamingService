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
@Entity(name = ContentMetaData.AUTHORS)
@Table(name = ContentMetaData.AUTHORS)
public class MediaAuthor extends MediaNameEntity{

    public MediaAuthor(String name, Instant uploadDate) {
        this.name = name;
        this.uploadDate = uploadDate;
    }
}
