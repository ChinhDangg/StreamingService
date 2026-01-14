package dev.chinh.streamingservice.persistence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.projection.MediaNameSearchItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media_metadata")
public class MediaMetaData extends MediaDescription {

    // Classification (will also be stored in search for fast information display)
    @JsonProperty(ContentMetaData.TAGS)
    @ManyToMany(cascade = { CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "tags_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "tags_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = { "media_id", "tags_id" })
    )
    private Set<MediaTag> tags;

    @JsonProperty(ContentMetaData.CHARACTERS)
    @ManyToMany(cascade = { CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "characters_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "characters_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = { "media_id", "characters_id" })
    )
    private Set<MediaCharacter> characters;

    @JsonProperty(ContentMetaData.UNIVERSES)
    @ManyToMany(cascade = { CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "universes_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "universes_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = { "media_id", "universes_id" })
    )
    private Set<MediaUniverse> universes;

    @JsonProperty(ContentMetaData.AUTHORS)
    @ManyToMany(cascade = { CascadeType.MERGE }, fetch = FetchType.LAZY)
    @JoinTable(
            name = "authors_media",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "authors_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = { "media_id", "authors_id" })
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
        return groupInfo == null ? null : isGrouper() ? groupInfo.getId() : groupInfo.getGrouperMetaDataId();
    }

    // Technical
    private short frameRate;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String absoluteFilePath;

    @Override
    public List<MediaNameSearchItem> getTags() {
        return tags == null ? null
                : tags.stream().map(n -> new MediaNameSearchItem(n.getId(), n.getName())).toList();
    }

    @Override
    public List<MediaNameSearchItem> getCharacters() {
        return characters == null ? null
                : characters.stream().map(n -> new MediaNameSearchItem(n.getId(), n.getName())).toList();
    }

    @Override
    public List<MediaNameSearchItem> getUniverses() {
        return universes == null ? null
                : universes.stream().map(n -> new MediaNameSearchItem(n.getId(), n.getName())).toList();
    }

    @Override
    public List<MediaNameSearchItem> getAuthors() {
        return authors == null ? null
                : authors.stream().map(n -> new MediaNameSearchItem(n.getId(), n.getName())).toList();
    }

    public void addUniverses(MediaUniverse universe) {
        universes = universes == null ? new HashSet<>() : universes;
        universes.add(universe);
    }
}
