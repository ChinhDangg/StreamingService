package dev.chinh.streamingservice.backend.content.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.content.service.AlbumService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/album")
public class AlbumController {

    private final AlbumService albumService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    @GetMapping("/{id}/{resolution}/{page}")
    public ResponseEntity<?> checkResizedImage(@PathVariable Long id,
                                                  @PathVariable Resolution resolution,
                                                  @PathVariable Integer page,
                                                  HttpServletRequest request) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            // if always show original - intercept the resolution parameter
            resolution = Resolution.original;
        }
        return ResponseEntity.ok().body(albumService.getAlbumContent(id, resolution, page, 25, request));
    }

    @GetMapping("/{albumId}/{albumRes}/vid/{vidRes}/{objectName}")
    public ResponseEntity<String> getAlbumVideoUrl(@PathVariable long albumId,
                                                   @PathVariable Resolution albumRes,
                                                   @PathVariable String objectName,
                                                   @PathVariable Resolution vidRes,
                                                   HttpServletRequest request) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            // if always show original - intercept the resolution parameter
            albumRes = Resolution.original;
            vidRes = Resolution.original;
        }
        return ResponseEntity.ok().body(albumService.getAlbumPartialVideoUrl(albumId, albumRes, objectName, vidRes, request));
    }
}
