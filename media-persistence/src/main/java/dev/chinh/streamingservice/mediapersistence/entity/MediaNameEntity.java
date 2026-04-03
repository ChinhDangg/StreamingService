package dev.chinh.streamingservice.mediapersistence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@MappedSuperclass
public abstract class MediaNameEntity {

    @Setter(AccessLevel.NONE)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected long id;

    @Setter(AccessLevel.NONE)
    @JsonProperty(value = ContentMetaData.USER_ID)
    @Column(nullable = false)
    protected long userId;

    @Column(unique = true, nullable = false)
    @JsonProperty(value = ContentMetaData.NAME)
    protected String name;

    @JsonProperty(ContentMetaData.LENGTH)
    protected int length;
}
