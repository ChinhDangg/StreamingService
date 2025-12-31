package dev.chinh.streamingservice.backend.content.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    @GetMapping("/original/{id}")
    public ResponseEntity<Void> getVideoUrl(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header("X-Accel-Redirect", videoService.getOriginalVideoUrl(id))
                .header("Content-Type", "video/mp4")
                .build();
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<String> preview(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(id));
    }

    @GetMapping("/partial/{id}/{resolution}")
    public ResponseEntity<?> getVideoAtDifferentResolutionUrl(@PathVariable Long id,
                                                    @PathVariable Resolution resolution) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            return getVideoUrl(id);
        }

        String url = videoService.getPartialVideoUrl(id, resolution);
        if (url.startsWith("/stream/redirect/object/")) {
            return ResponseEntity.ok()
                    .header("X-Accel-Redirect", url)
                    .header("Content-Type", "video/mp4")
                    .build();
        }

        return ResponseEntity.ok(url);
    }

}

