package dev.chinh.streamingservice.filemanager.upload;

import dev.chinh.streamingservice.filemanager.model.FileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadService uploadService;

    public record InitiateMultipartUploadRequest(@NotBlank @Size(max = 1000) String filePath) {}

    @PostMapping("/initiate")
    public ResponseEntity<FileResponse<?>> initiateUpload(@RequestBody InitiateMultipartUploadRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(uploadService.initiateUploadRequest(jwt.getSubject(), request.filePath)));
    }

    public record PresignUploadRequest(
            @Size(max = 256, message = "Invalid Upload ID length")
            String uploadId,
            @Max(1500)
            int partNumber) {}

    @PostMapping("/presign-part-url")
    public ResponseEntity<FileResponse<?>> getPresignPartUrl(@RequestBody @Valid PresignUploadRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(uploadService.generatePresignedPartUrl(jwt.getSubject(), request.uploadId, request.partNumber)));
    }

    public record EndSessionRequest(
            @Size(max = 256, message = "Invalid Upload ID length")
            String uploadId,
            List<UploadService.UploadedPart> uploadedParts,
            boolean isLast,
            boolean addAsVideo,
            String nameUpdateListAsJson
            ) {}

    @PostMapping("/end-session-file")
    public ResponseEntity<Void> endSessionFile(@RequestBody @Valid EndSessionRequest request, @AuthenticationPrincipal Jwt jwt) {
        uploadService.saveFile(jwt.getSubject(), request.uploadId, request.uploadedParts, request.isLast, request. addAsVideo, request.nameUpdateListAsJson);
        return ResponseEntity.ok().build();
    }
}
