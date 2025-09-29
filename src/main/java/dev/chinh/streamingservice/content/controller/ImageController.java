package dev.chinh.streamingservice.content.controller;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final ImageService imageService;

    @GetMapping("/resize/{bucket}")
    public ResponseEntity<Void> getResizedImage(@PathVariable String bucket,
                                          @RequestParam Resolution res, @RequestParam(name = "id") String videoId,
                                          HttpServletRequest request) throws Exception {
        return imageService.getResizedImageToResponse(bucket, videoId, res, request);
    }
}
