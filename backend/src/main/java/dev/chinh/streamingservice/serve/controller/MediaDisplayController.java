package dev.chinh.streamingservice.serve.controller;

import dev.chinh.streamingservice.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.serve.service.MediaDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/media")
public class MediaDisplayController {

    private final MediaDisplayService mediaDisplayService;

    @GetMapping("/content/{id}")
    public ResponseEntity<MediaDisplayContent> getMediaDisplayContent(@PathVariable long id){
        return ResponseEntity.ok().body(mediaDisplayService.getMediaContentInfo(id));
    }

    @GetMapping("/grouper-next/{id}")
    public ResponseEntity<Slice<Long>> getNextGrouper(@PathVariable long id,
                                                      @RequestParam int offset) {
        return ResponseEntity.ok().body(mediaDisplayService.getNextGroupOfMedia(id, offset));
    }
}
