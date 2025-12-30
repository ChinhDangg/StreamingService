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
    public ResponseEntity<String> getVideoUrl(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(videoService.getOriginalVideoUrl(id));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<String> preview(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(id));
    }

    @GetMapping("/partial/{id}/{resolution}")
    public ResponseEntity<String> getVideoAtDifferentResolutionUrl(@PathVariable Long id,
                                                    @PathVariable Resolution resolution) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            return ResponseEntity.ok(videoService.getOriginalVideoUrl(id));
        }
        return ResponseEntity.ok(videoService.getPartialVideoUrl(id, resolution));
    }

}

