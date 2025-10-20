package dev.chinh.streamingservice.content.controller;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final AlbumService albumService;

    @GetMapping("/album/{id}/{resolution}")
    public ResponseEntity<?> getAlbum(@PathVariable Long id,
                                      @PathVariable Resolution resolution,
                                      HttpServletRequest request) throws Exception {
        return ResponseEntity.ok().body(albumService.getAllMediaInAnAlbum(id, resolution, request));
    }

    @GetMapping("/original/{id}")
    public ResponseEntity<Void> getOriginalUrl(@PathVariable Long id,
                                               @RequestParam(name = "key") String imagePath) throws Exception {
        return albumService.getOriginalImageURLAsRedirectResponse(id, imagePath, 30 * 60);
    }

    @GetMapping("/resize/{id}")
    public ResponseEntity<Void> getResizedImage(@PathVariable Long id,
                                                @RequestParam Resolution res,
                                                @RequestParam(name = "key") String imagePath,
                                                HttpServletRequest request) throws Exception {
        return albumService.processResizedImageURLAsRedirectResponse(id, imagePath, res, request);
    }
}
