package dev.chinh.streamingservice.mediaupload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaBasicInfo {

    @NotBlank @Size(max = 255)
    private String title;

    @NotNull
    private short year;

    private MultipartFile thumbnail;

    public MediaBasicInfo(String title, short year) {
        this.title = title;
        this.year = year;
    }
}
