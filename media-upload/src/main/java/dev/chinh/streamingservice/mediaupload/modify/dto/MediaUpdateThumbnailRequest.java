package dev.chinh.streamingservice.mediaupload.modify.dto;

import dev.chinh.streamingservice.mediaupload.validation.ValidImage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MediaUpdateThumbnailRequest {

    private Double num;

    @ValidImage
    private MultipartFile thumbnail;
}
