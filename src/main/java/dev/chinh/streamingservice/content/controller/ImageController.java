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

    @GetMapping("/original/{bucket}")
    public ResponseEntity<Void> getOriginalUrl(@PathVariable String bucket, @RequestParam(name = "id") String imageId) throws Exception {
        return imageService.getOriginalImageURL(bucket, imageId, 300);
    }


    @GetMapping("/resize/{bucket}")
    public ResponseEntity<Void> getResizedImage(@PathVariable String bucket,
                                          @RequestParam Resolution res, @RequestParam(name = "imageId") String imageId,
                                          HttpServletRequest request) throws Exception {
        return imageService.getResizedImageToResponse(bucket, imageId, res, request);
    }
}
