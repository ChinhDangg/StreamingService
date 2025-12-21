package dev.chinh.streamingservice.mediaupload.controller;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.service.MediaUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload/media")
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    public record InitiateMultipartUploadRequest(String sessionId, String objectKey, MediaType mediaType) {}

    @GetMapping("/csrf-init")
    public ResponseEntity<Void> csrfTest(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // triggers the cookie
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/create-session")
    public String initiateSession(@RequestBody InitiateMultipartUploadRequest request) {
        return mediaUploadService.initiateMediaUploadRequest(request.objectKey, request.mediaType);
    }

    @PostMapping("/initiate")
    public String initiateUpload(@RequestBody InitiateMultipartUploadRequest request) {
        return mediaUploadService.initiateMultipartUploadRequest(request.sessionId, request.objectKey, request.mediaType);
    }

    public record PresignUploadRequest(
            String sessionId,
            String uploadId,
            String objectKey,
            int partNumber) {}

    @PostMapping("/presign-part-url")
    public String getPresignPartUrl(@RequestBody PresignUploadRequest request) {
        return mediaUploadService.generatePresignedPartUrl(request.sessionId, request.uploadId, request.objectKey, request.partNumber);
    }

    public record CompleteMultipartUploadRequest(
            String sessionId,
            String uploadId,
            String objectKey,
            List<MediaUploadService.UploadedPart> uploadedParts) {}

    @PostMapping("/complete")
    public void completeUpload(@RequestBody CompleteMultipartUploadRequest request) {
        mediaUploadService.completeMultipartUpload(request.sessionId, request.uploadId, request.objectKey, request.uploadedParts);
    }

    public record EndSessionRequest(String sessionId, MediaBasicInfo basicInfo) {}

    @PostMapping("/end-session")
    public ResponseEntity<Long> endSession(@RequestBody EndSessionRequest request) throws Exception {
        return ResponseEntity.ok().body(mediaUploadService.saveMedia(request.sessionId, request.basicInfo));
    }

    @PostMapping(value = "/create-grouper", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createGrouperMedia(@Valid @ModelAttribute MediaBasicInfo basicInfo) {
        return ResponseEntity.ok().body(mediaUploadService.saveGrouperMedia(basicInfo));
    }

    public record EndSessionGrouperRequest(String sessionId, long grouperMediaId, String title) {}

    @PostMapping("/end-session-grouper")
    public ResponseEntity<Long> endSessionGrouper(@RequestBody EndSessionGrouperRequest request) throws Exception {
        return ResponseEntity.ok().body(mediaUploadService.saveMediaInGrouper(request.sessionId, request.grouperMediaId, request.title));
    }

}