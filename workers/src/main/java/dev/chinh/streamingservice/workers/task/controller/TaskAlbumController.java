package dev.chinh.streamingservice.workers.task.controller;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.workers.task.service.TaskAlbumService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/album")
public class TaskAlbumController {

    private final TaskAlbumService taskAlbumService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    @GetMapping("/{id}/{resolution}")
    public ResponseEntity<?> checkResizedImage(@PathVariable Long id,
                                               @PathVariable Resolution resolution,
                                               @RequestParam(name = "p", required = false) int page,
                                               @RequestParam(name = "nc", required = false) String nextCursor,
                                               HttpServletRequest request,
                                               @AuthenticationPrincipal Jwt jwt) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            // if always show original - intercept the resolution parameter
            resolution = Resolution.original;
        }
        return ResponseEntity.ok().body(taskAlbumService.getAlbumContent(jwt.getSubject(), id, resolution, page, 25, nextCursor, request));
    }

    @GetMapping("/{albumId}/{albumRes}/vid/{objectName}/{vidRes}")
    public ResponseEntity<?> getAlbumVideoUrl(@PathVariable long albumId,
                                                   @PathVariable Resolution albumRes,
                                                   @PathVariable String objectName,
                                                   @PathVariable Resolution vidRes,
                                                   HttpServletRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) throws Exception {
        if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
            // if always show original - intercept the resolution parameter
            albumRes = Resolution.original;
            vidRes = Resolution.original;
        }
        return ResponseEntity.ok().body(taskAlbumService.getAlbumPartialVideoUrl(jwt.getSubject(), albumId, albumRes, objectName, vidRes, request));
    }
}
