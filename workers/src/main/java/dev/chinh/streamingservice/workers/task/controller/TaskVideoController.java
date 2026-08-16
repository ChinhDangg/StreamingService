package dev.chinh.streamingservice.workers.task.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.workers.task.service.TaskVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/videos")
public class TaskVideoController {

    private final TaskVideoService taskVideoService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    @GetMapping("/original/{id}")
    public ResponseEntity<?> getVideoUrl(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
//        return ResponseEntity.ok()
//                .header("X-Accel-Redirect", videoService.getOriginalVideoUrl(jwt.getSubject(), id))
//                .header("Content-Type", "video/mp4")
//                .build();
        return ResponseEntity.ok().body(taskVideoService.getOriginalVideoUrl(jwt.getSubject(), id));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<?> preview(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok(taskVideoService.getPreviewVideoUrl(jwt.getSubject(), id));
    }

    @GetMapping("/partial/{id}/{resolution}")
    public ResponseEntity<?> getVideoAtDifferentResolutionUrl(@PathVariable Long id,
                                                              @PathVariable Resolution resolution,
                                                              @AuthenticationPrincipal Jwt jwt) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            return ResponseEntity.ok(taskVideoService.getOriginalVideoUrl(jwt.getSubject(), id));
        }
        return ResponseEntity.ok(taskVideoService.getPartialVideoUrl(jwt.getSubject(), id, resolution));
    }
}