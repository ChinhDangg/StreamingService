package dev.chinh.streamingservice.data.entity;

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
@Table(name = "universes")
public class MediaUniverse extends MediaNameEntityWithThumbnail {

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "characters_universes",
            joinColumns = @JoinColumn(name = "universes_id"),
            inverseJoinColumns = @JoinColumn(name = "characters_id")
    )
    private Set<MediaCharacter> characters = new HashSet<>();

    public MediaUniverse(String name, Instant uploadDate) {
        this.name = name;
        this.uploadDate = uploadDate;
    }
}
