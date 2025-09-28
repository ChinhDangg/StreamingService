package dev.chinh.streamingservice;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    @GetMapping("/original/{object}")
    public ResponseEntity<String> getVideoUrl(@PathVariable String object) throws Exception {
        String bucket = "testminio";
        String url = videoService.getSignedUrlForHostNginx(bucket, object, 300); // 5 minutes
        return ResponseEntity.ok(url);
    }

    @GetMapping("/preview/{videoId}")
    public ResponseEntity<String> preview(@PathVariable String videoId) throws Exception {
        return ResponseEntity.ok(videoService.getPreviewVideoUrl(videoId));
    }

    @GetMapping("/partial/{videoId}")
    private ResponseEntity<String> getTranscodeFull(@PathVariable String videoId) throws Exception {
        return ResponseEntity.ok(videoService.getPartialVideoUrl(videoId, Resolution.p360));
    }

}

