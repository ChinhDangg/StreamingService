package dev.chinh.streamingservice.filemanager.data;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Document(collection = "folder_locks")
public class FolderLocks {

    @Id
    private String id;

    private final String userId;

    @Indexed(expireAfter = "PT0S")
    private final Date expiryTime = Date.from(new Date().toInstant().plusSeconds(900));

    public FolderLocks(String id, String userId) {
        this.id = id;
        this.userId = userId;
    }
}
