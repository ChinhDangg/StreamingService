package dev.chinh.streamingservice.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media")
public class MediaMetaData extends MediaDescription {

    // Classification (will also be stored in search for fast information display)
    @JsonProperty(ContentMetaData.TAGS)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "tags_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "tags_id")
    )
    private List<MediaTag> tags;

    @JsonProperty(ContentMetaData.CHARACTERS)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "characters_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "characters_id")
    )
    private List<MediaCharacter> characters;

    @JsonProperty(ContentMetaData.UNIVERSES)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "universes_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "universes_id")
    )
    private List<MediaUniverse> universes;

    @JsonProperty(ContentMetaData.AUTHORS)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "authors_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "authors_id")
    )
    private List<MediaAuthor> authors;

    // Technical
    @Column(nullable = false)
    private short frameRate;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String absoluteFilePath;

    // Grouping (optional)
    @OneToOne(fetch = FetchType.LAZY)
    private MediaGroupMetaData groupMetaData;
}
