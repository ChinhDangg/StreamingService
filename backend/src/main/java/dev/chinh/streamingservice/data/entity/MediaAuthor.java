package dev.chinh.streamingservice.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "authors")
public class MediaAuthor extends MediaNameEntity{

    public MediaAuthor(String name) {
        this.name = name;
    }
}
