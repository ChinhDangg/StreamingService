package dev.chinh.streamingservice.filemanager;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Builder
@Getter
@ToString
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
    private String path; // use chain of parentId with comma separated or maybe just use array - possible to index too

    private long mId;
    private String name;
    private FileType fileType;

    private String key;
    private String thumbnail;

    private Long size;
    private LocalDateTime uploadDate;
}
