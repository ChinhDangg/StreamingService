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

    @GetMapping("/original/{bucket}")
    public ResponseEntity<String> getVideoUrl(@PathVariable String bucket,
                                              @RequestParam("id") String videoId) throws Exception {
        String url = videoService.getOriginalVideoUrl(bucket, videoId);
        return ResponseEntity.ok(url);
    }

    @GetMapping("/preview/{bucket}")
    public ResponseEntity<String> preview(@PathVariable String bucket,
                                          @RequestParam("id") String videoId) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(bucket, videoId));
    }

    @GetMapping("/partial/{bucket}")
    private ResponseEntity<String> getTranscodeFull(@PathVariable String bucket,
                                                    @RequestParam("id") String videoId) throws Exception {
        return ResponseEntity.ok(videoService.getPartialVideoUrl(bucket, videoId, Resolution.p360));
    }

}

