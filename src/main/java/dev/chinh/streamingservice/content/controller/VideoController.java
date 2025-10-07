package dev.chinh.streamingservice.content.controller;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    @GetMapping("/original/{id}")
    public ResponseEntity<String> getVideoUrl(@PathVariable Long id) throws Exception {
        String url = videoService.getOriginalVideoUrl(id);
        return ResponseEntity.ok(url);
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<String> preview(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(id));
    }

    @GetMapping("/partial/{id}")
    private ResponseEntity<String> getTranscodeFull(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(videoService.getPartialVideoUrl(id, Resolution.p360));
    }

}

