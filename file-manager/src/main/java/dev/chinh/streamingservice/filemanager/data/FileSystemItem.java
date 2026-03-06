package dev.chinh.streamingservice.filemanager.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Builder
@Data
@Document(collection = "fs_metadata")
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "path_name_idx", def = "{'path': 1, 'name': 1}"),

        // SortBy.UPLOAD: Usually we want newest first (-1)
        @CompoundIndex(name = "parent_upload_idx", def = "{'parentId': 1, 'uploadDate': -1}"),
        // SortBy.NAME: Usually alphabetical (1)
        @CompoundIndex(name = "parent_name_idx", def = "{'parentId': 1, 'name': 1}", unique = true),
        // SortBy.SIZE: Smallest or Largest (1 covers both)
        @CompoundIndex(name = "parent_size_idx", def = "{'parentId': 1, 'size': 1}"),
        // SortBy.LENGTH:
        @CompoundIndex(name = "parent_length_idx", def = "{'parentId': 1, 'length': 1}")
})
public class FileSystemItem {

    @JsonProperty(ContentMetaData.ID)
    @Id
    private String id;

    private String parentId;

    @Indexed
    private String path;

    private FileType fileType;

    @Indexed
    private Long mId;
    @JsonProperty(ContentMetaData.NAME)
    private String name;
    @JsonProperty(ContentMetaData.BUCKET)
    private String bucket;
    @JsonProperty(ContentMetaData.OBJECT_NAME)
    private String objectName;
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;
    @JsonProperty(ContentMetaData.SIZE)
    private Long size;
    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private Instant uploadDate;

    private Short statusCode;

    public static String getStatusCodeAsString(Short statusCode) {
        if (statusCode == null) return null;
        if (statusCode == FileStatus.PROCESSING.getValue()) return "Processing as media in progress";
        if (statusCode == FileStatus.DELETING.getValue()) return "Deleting in progress";
        if (statusCode == FileStatus.IN_USE.getValue()) return "In use";
        return null;
    }
}
