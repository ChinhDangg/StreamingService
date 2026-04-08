package dev.chinh.streamingservice.backend.serve.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.chinh.streamingservice.backend.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.backend.serve.service.MediaDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/media")
public class MediaDisplayController {

    private final MediaDisplayService mediaDisplayService;

    @GetMapping("/content/{id}")
    public ResponseEntity<MediaDisplayContent> getMediaDisplayContent(@PathVariable long id,
                                                                      @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok().body(mediaDisplayService.getMediaContentInfo(jwt.getSubject(), id));
    }

    @GetMapping("/grouper-next/{id}")
    public ResponseEntity<MediaDisplayService.GroupSlice> getNextGrouper(@PathVariable long id,
                                                                         @RequestParam(name = "p") int page,
                                                                         @RequestParam(name = "order") Sort.Direction order,
                                                                         @AuthenticationPrincipal Jwt jwt) throws JsonProcessingException {
        return ResponseEntity.ok().body(mediaDisplayService.getNextGroupOfMedia(jwt.getSubject(), id, page, order));
    }

    @GetMapping("/content-page/{id}")
    public ResponseEntity<Void> getMediaPage(@PathVariable long id,
                                             @AuthenticationPrincipal Jwt jwt) {
        return mediaDisplayService.getServePageTypeFromMedia(jwt.getSubject(), id);
    }
}
