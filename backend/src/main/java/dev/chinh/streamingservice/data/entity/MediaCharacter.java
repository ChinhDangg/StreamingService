package dev.chinh.streamingservice.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
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
public class MediaCharacter extends MediaNameEntity {

    @ManyToMany(mappedBy = "characters", fetch = FetchType.LAZY)
    private Set<MediaUniverse> universe = new HashSet<>();

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    public MediaCharacter(String name) {
        this.name = name;
    }
}