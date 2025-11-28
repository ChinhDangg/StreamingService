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
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(id, MediaNameEntityConstant.authors));
    }

    @GetMapping("/characters/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaCharacterInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(id, MediaNameEntityConstant.characters));
    }

    @GetMapping("/universes/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaUniverseInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(id, MediaNameEntityConstant.universes));
    }

    @GetMapping("/tags/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaTagInfo(@PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(id, MediaNameEntityConstant.tags));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<List<String>> updateMediaAuthorInfo(@PathVariable long id,
                                                      @RequestBody MediaMetadataModifyService.UpdateList updateList) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateNameEntityInMedia(updateList, id));
    }

}
