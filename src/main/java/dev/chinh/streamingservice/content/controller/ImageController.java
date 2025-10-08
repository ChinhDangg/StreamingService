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

    @GetMapping("/album/{id}/{resolution}")
    public ResponseEntity<?> getAlbum(@PathVariable Long id, @PathVariable Resolution resolution) throws Exception {
        return ResponseEntity.ok().body(imageService.getAllMediaInAnAlbum(id, resolution));
    }

    @GetMapping("/original/{id}")
    public ResponseEntity<Void> getOriginalUrl(@PathVariable Long id,
                                               @RequestParam(name = "key") String imagePath) throws Exception {
        return imageService.getOriginalImageURL(id, imagePath, 30 * 60);
    }

    @GetMapping("/resize/{id}")
    public ResponseEntity<Void> getResizedImage(@PathVariable Long id,
                                                @RequestParam Resolution res,
                                                @RequestParam(name = "key") String imagePath,
                                                HttpServletRequest request) throws Exception {
        return imageService.getResizedImageURL(id, imagePath, res, request);
    }
}
