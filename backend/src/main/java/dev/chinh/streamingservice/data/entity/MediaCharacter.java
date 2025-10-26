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
@Table(name = "characters")
public class MediaCharacter {

    @Setter(AccessLevel.NONE)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "characters", fetch = FetchType.LAZY)
    private Set<MediaUniverse> universe = new HashSet<>();

    public MediaCharacter(String name) {
        this.name = name;
    }
}