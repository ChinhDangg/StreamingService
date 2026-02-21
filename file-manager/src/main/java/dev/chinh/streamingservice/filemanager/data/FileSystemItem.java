package dev.chinh.streamingservice.filemanager.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Builder
@Data
@Document(collection = "fs_metadata")
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "parent_name_unique_idx", def = "{'parentId': 1, 'name': 1}", unique = true)
public class FileSystemItem {

    @JsonProperty(ContentMetaData.ID)
    @Id
    private String id;

    @Indexed
    private String parentId;

    @Indexed
    private String path;

    private FileType fileType;

    @Indexed
    private Long mId;
    @JsonProperty(ContentMetaData.NAME)
    private String name;
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
    @JsonProperty(ContentMetaData.SIZE)
    private Long size;
    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private Instant uploadDate;

    private Short statusCode;
}
