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

    @GetMapping("/{nameEntity}/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaNameEntityInfo(@PathVariable MediaNameEntityConstant nameEntity,
                                                                      @PathVariable long id) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(id, nameEntity));
    }

    @PutMapping("/title/{id}")
    public ResponseEntity<String> updateMediaTitle(@PathVariable long id, @RequestBody String title) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateMediaTitle(id, title));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<List<String>> updateMediaAuthorInfo(@PathVariable long id,
                                                      @RequestBody MediaMetadataModifyService.UpdateList updateList) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateNameEntityInMedia(updateList, id));
    }

}
