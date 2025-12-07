package dev.chinh.streamingservice.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

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
    private Set<MediaTag> tags;

    @JsonProperty(ContentMetaData.CHARACTERS)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "characters_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "characters_id")
    )
    private Set<MediaCharacter> characters;

    @JsonProperty(ContentMetaData.UNIVERSES)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "universes_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "universes_id")
    )
    private Set<MediaUniverse> universes;

    @JsonProperty(ContentMetaData.AUTHORS)
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "authors_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "authors_id")
    )
    private Set<MediaAuthor> authors;

    // Grouping (optional)
    @JsonProperty(ContentMetaData.GROUP_INFO)
    @OneToOne(mappedBy = "mediaMetaData",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private MediaGroupMetaData groupInfo;

    @Override
    public boolean isGrouper() {
        return groupInfo != null && (groupInfo.getGrouperMetaDataId() == null || groupInfo.getGrouperMetaDataId() == -1);
    }

    @Override
    public Long getGrouperId() {
        return groupInfo == null ? null : groupInfo.getGrouperMetaDataId();
    }

    // Technical
    private short frameRate;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String absoluteFilePath;

    @Override
    public List<String> getTags() {
        return tags == null ? null : tags.stream().map(MediaTag::getName).toList();
    }

    @Override
    public List<String> getCharacters() {
        return characters == null ? null : characters.stream().map(MediaCharacter::getName).toList();
    }

    @Override
    public List<String> getUniverses() {
        return universes == null ? null : universes.stream().map(MediaUniverse::getName).toList();
    }

    @Override
    public List<String> getAuthors() {
        return authors == null ? null : authors.stream().map(MediaAuthor::getName).toList();
    }
}
