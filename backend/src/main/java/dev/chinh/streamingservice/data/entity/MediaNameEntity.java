package dev.chinh.streamingservice.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@MappedSuperclass
public abstract class MediaNameEntity {

    @Setter(AccessLevel.NONE)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(unique = true, nullable = false)
    @JsonProperty(value = ContentMetaData.NAME)
    protected String name;

    @JsonProperty(ContentMetaData.LENGTH)
    protected int length;

    @Column(nullable = false)
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    protected LocalDate uploadDate;
}
