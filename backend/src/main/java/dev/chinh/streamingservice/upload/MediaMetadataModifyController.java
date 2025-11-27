package dev.chinh.streamingservice.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/modify/media")
public class MediaMetadataModifyController {

    private final MediaMetadataModifyService mediaMetadataModifyService;

    @GetMapping("/authors/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaAuthorInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaAuthorInfo(id));
    }

    @GetMapping("/characters/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaCharacterInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaCharacterInfo(id));
    }

    @GetMapping("/universes/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaUniverseInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaUniverseInfo(id));
    }

    @GetMapping("/tags/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaTagInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaTagInfo(id));
    }

    @PostMapping("/update/{id}")
    public ResponseEntity<Void> updateMediaAuthorInfo(@PathVariable long id,
                                                      @RequestBody MediaMetadataModifyService.UpdateList updateList) {
        mediaMetadataModifyService.updateNameEntityInMedia(updateList, id);
        return ResponseEntity.ok().build();
    }

}
