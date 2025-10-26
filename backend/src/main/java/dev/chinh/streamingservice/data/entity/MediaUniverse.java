package dev.chinh.streamingservice.data.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "universes")
public class MediaUniverse {

    @Setter(AccessLevel.NONE)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "characters_universes",
            joinColumns = @JoinColumn(name = "universes_id"),
            inverseJoinColumns = @JoinColumn(name = "characters_id")
    )
    private Set<MediaCharacter> characters = new HashSet<>();

    public MediaUniverse(String name) {
        this.name = name;
    }
}
