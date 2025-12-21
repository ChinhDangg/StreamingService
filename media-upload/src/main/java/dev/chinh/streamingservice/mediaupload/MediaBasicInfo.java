package dev.chinh.streamingservice.mediaupload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaBasicInfo {

    @NotBlank
    private String title;

    @NotNull
    private short year;

    private MultipartFile thumbnail;

    public MediaBasicInfo(String title, short year) {
        this.title = title;
        this.year = year;
    }
}
