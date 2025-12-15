package dev.chinh.streamingservice.content.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/album")
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping("/{id}/{resolution}")
    public ResponseEntity<?> getAlbum(@PathVariable Long id,
                                      @PathVariable Resolution resolution,
                                      HttpServletRequest request) throws Exception {
        return ResponseEntity.ok().body(albumService.getAllMediaUrlInAnAlbum(id, resolution, request));
    }

    @GetMapping("/{id}/{resolution}/{offset}/check-resized")
    public ResponseEntity<?> checkResizedImage(@PathVariable Long id,
                                                  @PathVariable Resolution resolution,
                                                  @PathVariable Integer offset,
                                                  HttpServletRequest request) throws Exception {
        return ResponseEntity.ok().body(albumService.processResizedAlbumImagesInBatch(id, resolution, offset, 5, request));
    }

    @GetMapping("/{albumId}/{albumRes}/vid/{vidNum}/{vidRes}")
    public ResponseEntity<String> getAlbumVideoUrl(@PathVariable long albumId,
                                                   @PathVariable Resolution albumRes,
                                                   @PathVariable int vidNum,
                                                   @PathVariable Resolution vidRes,
                                                   HttpServletRequest request) throws Exception {
        return ResponseEntity.ok().body(albumService.getAlbumPartialVideoUrl(albumId, albumRes, vidNum, vidRes, request));
    }
}
