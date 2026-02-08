package dev.chinh.streamingservice.filemanager;

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
