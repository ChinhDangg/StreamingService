package dev.chinh.streamingservice.mediaupload.modify.controller;

import dev.chinh.streamingservice.mediaupload.modify.dto.MediaUpdateThumbnailRequest;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/modify/media")
public class MediaMetadataModifyController {

    private final MediaMetadataModifyService mediaMetadataModifyService;

    @GetMapping("/{nameEntity}/{id}")
    public ResponseEntity<List<NameEntityDTO>> getMediaNameEntityInfo(@PathVariable MediaNameEntityConstant nameEntity,
                                                                      @PathVariable long id,
                                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.getMediaNameEntityInfo(jwt.getSubject(), id, nameEntity));
    }

    @PutMapping("/title/{id}")
    public ResponseEntity<String> updateMediaTitle(@PathVariable long id, @RequestBody String title, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateMediaTitle(jwt.getSubject(), id, title));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<List<NameEntityDTO>> updateMediaNameEntityInfo(@PathVariable long id,
                                                                         @RequestBody MediaMetadataModifyService.UpdateList updateList,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaMetadataModifyService.updateNameEntityInMedia(jwt.getSubject(), updateList, id, true, null));
    }

    @PutMapping("/update-batch/{id}")
    public ResponseEntity<Void> updateMediaNameEntityInfoInBatch(@PathVariable long id,
                                                                 @RequestBody List<MediaMetadataModifyService.UpdateList> updateLists,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        mediaMetadataModifyService.updateNameEntityInMediaInBatch(jwt.getSubject(), updateLists, id, false);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/thumbnail/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateMediaThumbnail(@PathVariable long id,
                                                     @Valid @ModelAttribute MediaUpdateThumbnailRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) throws Exception {
        mediaMetadataModifyService.updateMediaThumbnail(jwt.getSubject(), id, request.getNum(), request.getThumbnail());
        return ResponseEntity.ok().build();
    }

}
