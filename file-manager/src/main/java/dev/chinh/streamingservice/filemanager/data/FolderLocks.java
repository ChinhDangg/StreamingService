package dev.chinh.streamingservice.filemanager.data;

import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Getter
@Document(collection = "folder_locks")
public class FolderLocks {

    @Id
    private String id;

    @Field(FileItemField.USER_ID)
    private final String userId;

    @Field(FileItemField.STATUS_CODE)
    private final FileStatus statusCode;

    @Indexed(expireAfter = "PT0S")
    private final Date expiryTime = Date.from(new Date().toInstant().plusSeconds(900));

    public FolderLocks(String id, String userId, FileStatus statusCode) {
        this.id = id;
        this.userId = userId;
        this.statusCode = statusCode;
    }
}
