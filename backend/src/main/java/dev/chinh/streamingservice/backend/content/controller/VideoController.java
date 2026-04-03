package dev.chinh.streamingservice.backend.content.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    @GetMapping("/original/{id}")
    public ResponseEntity<?> getVideoUrl(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
//        return ResponseEntity.ok()
//                .header("X-Accel-Redirect", videoService.getOriginalVideoUrl(jwt.getSubject(), id))
//                .header("Content-Type", "video/mp4")
//                .build();
        return ResponseEntity.ok().body(videoService.getOriginalVideoUrl(jwt.getSubject(), id));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<String> preview(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(jwt.getSubject(), id));
    }

    @GetMapping("/partial/{id}/{resolution}")
    public ResponseEntity<?> getVideoAtDifferentResolutionUrl(@PathVariable Long id,
                                                              @PathVariable Resolution resolution,
                                                              @AuthenticationPrincipal Jwt jwt) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            return ResponseEntity.ok(videoService.getOriginalVideoUrl(jwt.getSubject(), id));
        }
        return ResponseEntity.ok(videoService.getPartialVideoUrl(jwt.getSubject(), id, resolution));
    }

}

