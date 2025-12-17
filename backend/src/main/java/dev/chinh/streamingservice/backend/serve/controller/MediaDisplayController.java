package dev.chinh.streamingservice.backend.serve.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.chinh.streamingservice.backend.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.backend.serve.service.MediaDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/media")
public class MediaDisplayController {

    private final MediaDisplayService mediaDisplayService;

    @GetMapping("/content/{id}")
    public ResponseEntity<MediaDisplayContent> getMediaDisplayContent(@PathVariable long id) throws JsonProcessingException {
        return ResponseEntity.ok().body(mediaDisplayService.getMediaContentInfo(id));
    }

    @GetMapping("/grouper-next/{id}")
    public ResponseEntity<MediaDisplayService.GroupSlice> getNextGrouper(@PathVariable long id,
                                                                         @RequestParam(name = "offset") int offset,
                                                                         @RequestParam(name = "order") Sort.Direction order) throws JsonProcessingException {
        return ResponseEntity.ok().body(mediaDisplayService.getNextGroupOfMedia(id, offset, order));
    }

    @GetMapping("/content-page/{id}")
    public ResponseEntity<Void> getMediaPage(@PathVariable long id) {
        return mediaDisplayService.getServePageTypeFromMedia(id);
    }
}
