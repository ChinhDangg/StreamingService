package dev.chinh.streamingservice.mediaupload.modify.controller;

import dev.chinh.streamingservice.mediaupload.modify.dto.MediaUpdateThumbnailRequest;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
    public ResponseEntity<List<NameEntityDTO>> updateMediaNameEntityInfo(@PathVariable long id,
                                                      @RequestBody MediaMetadataModifyService.UpdateList updateList) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateNameEntityInMedia(updateList, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedia(@PathVariable long id) {
        mediaMetadataModifyService.deleteMedia(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/thumbnail/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateMediaThumbnail(@PathVariable long id,
                                                     @Valid @ModelAttribute MediaUpdateThumbnailRequest request) throws Exception {
        mediaMetadataModifyService.updateMediaThumbnail(id, request.getNum(), request.getThumbnail());
        return ResponseEntity.ok().build();
    }

}
