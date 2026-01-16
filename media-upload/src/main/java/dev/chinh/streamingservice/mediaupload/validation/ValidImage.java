package dev.chinh.streamingservice.mediaupload.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER}) // Can be used on fields and parameters
@Retention(RetentionPolicy.RUNTIME) // Retained at runtime
@Constraint(validatedBy = ImageFileValidator.class) // links to the logic class
public @interface ValidImage {
    String message() default "Invalid image file. Must be a PNG, JPEG, GIF, or WEBP file.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
