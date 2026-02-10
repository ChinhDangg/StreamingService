package dev.chinh.streamingservice.filemanager;

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

    @Id
    private String id;

    @Indexed
    private String parentId;

    @Indexed
    private String path;

    private FileType fileType;

    private Long mId;
    private String name;
    private String thumbnail;
    private Long size;
    private Instant uploadDate;
}
