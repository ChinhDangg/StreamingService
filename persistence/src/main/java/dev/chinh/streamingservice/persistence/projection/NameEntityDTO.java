package dev.chinh.streamingservice.persistence.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NameEntityDTO {

    @JsonProperty(ContentMetaData.ID)
    private long id;

    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    public NameEntityDTO(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NameEntityDTO that = (NameEntityDTO) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
