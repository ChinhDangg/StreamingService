package dev.chinh.streamingservice.mediaupload.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ImageFileValidator implements ConstraintValidator<ValidImage, MultipartFile> {

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext constraintValidatorContext) {
        if (file == null || file.isEmpty())
            return true;

        String contentType = file.getContentType();
        if (!(contentType != null && ALLOWED_MIME_TYPES.contains(contentType))) {
            return false;
        }

        try {
            Tika tika = new Tika();
            String mimeType = tika.detect(file.getInputStream());
            return ALLOWED_MIME_TYPES.contains(mimeType);
        } catch (IOException e) {
            return false;
        }
    }
}
