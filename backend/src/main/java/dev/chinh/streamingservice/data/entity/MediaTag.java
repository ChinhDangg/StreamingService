package dev.chinh.streamingservice.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tags")
public class MediaTag extends MediaNameEntity {

    public MediaTag(String name, Instant uploadDate) {
        this.name = name;
        this.uploadDate = uploadDate;
    }
}
