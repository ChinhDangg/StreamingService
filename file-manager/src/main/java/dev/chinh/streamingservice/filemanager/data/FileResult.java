package dev.chinh.streamingservice.filemanager.data;

import dev.chinh.streamingservice.filemanager.constant.FileType;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FileResult {

    private FileType fileType;
    private String name;
}
