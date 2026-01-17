package dev.chinh.streamingservice.mediaupload.modify.dto;

import dev.chinh.streamingservice.mediaupload.validation.ValidImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NameAndThumbnailPostRequest {

    @NotBlank
    private String name;

    @NotNull @ValidImage
    private MultipartFile thumbnail;
}
