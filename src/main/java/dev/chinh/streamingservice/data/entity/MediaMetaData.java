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
public class MediaMetaData implements MediaDescriptor {

    // Classification (will also be stored in search for fast information display)
    @JsonProperty(ContentMetaData.ID)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty(ContentMetaData.BUCKET)
    @Column(nullable = false)
    private String bucket;

    @JsonProperty(ContentMetaData.PARENT_PATH)
    @Column(nullable = false)
    private String parentPath;

    @JsonProperty(ContentMetaData.KEY)
    private String key;        // if key exist then is an individual content, otherwise use parentPath for grouping

    @JsonProperty(ContentMetaData.THUMBNAIL)
    @Column(nullable = false)
    private String thumbnail;

    @JsonProperty(ContentMetaData.WIDTH)
    @Column(nullable = false)
    private Integer width;

    @JsonProperty(ContentMetaData.HEIGHT)
    @Column(nullable = false)
    private Integer height;

    // Search Fields
    @JsonProperty(ContentMetaData.TITLE)
    @Column(nullable = false)
    private String title;

    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    @Column(nullable = false)
    private LocalDate uploadDate;

    @JsonProperty(ContentMetaData.YEAR)
    @Column(nullable = false)
    private Integer year;

    @JsonProperty(ContentMetaData.LENGTH)
    @Column(nullable = false)
    private Integer length;

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
    @ManyToOne(fetch = FetchType.LAZY)
    private MediaGroupMetaData groupMetaData;

    public String getPath() {
        return parentPath + (key == null ? "" : ('/' + key));
    }

    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public boolean hasThumbnail() {
        return thumbnail != null && !thumbnail.isEmpty();
    }
}
