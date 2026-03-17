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
import org.springframework.data.mongodb.core.mapping.Field;

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
        @CompoundIndex(name = "parent_length_idx", def = "{'parentId': 1, 'length': 1}"),
        // SortBy.RESOLUTION: (Usually want the largest area to be first)
        @CompoundIndex(name = "res_area_width_idx", def = "{'res.a': -1, 'res.w': -1}")
})
public class FileSystemItem {

    public record ResolutionInfo(
            @Field(FileItemField.WIDTH)
            int width,

            @Field(FileItemField.HEIGHT)
            int height,

            @Field(FileItemField.AREA)
            @Indexed long area) {
        // Custom constructor to auto-calculate the area 'a'
        public ResolutionInfo(int width, int height) {
            this(width, height, (long) width * height);
        }
    }

    @JsonProperty(ContentMetaData.ID)
    @Id
    private String id;

    @Field(FileItemField.PARENT_ID)
    private String parentId;

    @Field(FileItemField.PATH)
    @Indexed
    private String path;

    @Field(FileItemField.FILE_TYPE)
    private FileType fileType;

    @Field(FileItemField.MEDIA_ID)
    @Indexed
    private Long mId;

    @Field(FileItemField.NAME)
    @JsonProperty(ContentMetaData.NAME)
    private String name;

    @Field(FileItemField.BUCKET)
    @JsonProperty(ContentMetaData.BUCKET)
    private String bucket;

    @Field(FileItemField.OBJECT_NAME)
    @JsonProperty(ContentMetaData.OBJECT_NAME)
    private String objectName;

    @Field(FileItemField.THUMBNAIL)
    @JsonProperty(ContentMetaData.THUMBNAIL)
    private String thumbnail;

    @Field(FileItemField.SIZE)
    @JsonProperty(ContentMetaData.SIZE)
    private Long size;

    @Field(FileItemField.LENGTH)
    @JsonProperty(ContentMetaData.LENGTH)
    private Integer length;

    @Field(FileItemField.RESOLUTION_INFO)
    private ResolutionInfo resolutionInfo;

    @Field(FileItemField.UPLOAD_DATE)
    @JsonProperty(ContentMetaData.UPLOAD_DATE)
    private Instant uploadDate;

    @Field(FileItemField.STATUS_CODE)
    private Short statusCode;

    public static String getStatusCodeAsString(Short statusCode) {
        if (statusCode == null) return null;
        if (statusCode == FileStatus.PROCESSING.getValue()) return "Processing as media in progress";
        if (statusCode == FileStatus.DELETING.getValue()) return "Deleting in progress";
        if (statusCode == FileStatus.IN_USE.getValue()) return "In use";
        return null;
    }

    public void setResolutionInfo(int w, int h) {
        this.resolutionInfo = new ResolutionInfo(w, h);
    }

    public int getWidth() {
        return resolutionInfo.width;
    }

    public int getHeight() {
        return resolutionInfo.height;
    }

    public long getArea() {
        return resolutionInfo.area;
    }
}