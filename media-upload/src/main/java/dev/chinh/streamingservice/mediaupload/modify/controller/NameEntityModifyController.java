package dev.chinh.streamingservice.mediaupload.modify.controller;

import dev.chinh.streamingservice.mediaupload.modify.dto.NameAndThumbnailPostRequest;
import dev.chinh.streamingservice.mediaupload.modify.service.NameEntityModifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/modify/name")
public class NameEntityModifyController {

    private final NameEntityModifyService nameEntityModifyService;

    @PostMapping("/authors")
    public ResponseEntity<Void> addAuthor(@RequestBody String name) {
        nameEntityModifyService.addAuthor(name);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(value = "/characters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> addCharacter(@Valid @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.addCharacter(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(value = "/universes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> addUniverse(@Valid @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.addUniverse(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/tags")
    public ResponseEntity<Void> addTag(@RequestBody String name) {
        nameEntityModifyService.addTag(name);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @PutMapping("/authors/{id}")
    public ResponseEntity<Void> updateAuthor(@PathVariable long id, @RequestBody String name) {
        nameEntityModifyService.updateAuthor(id, name);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/characters/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateCharacter(@PathVariable long id,
                                                @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.updateCharacter(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/universes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateUniverse(@PathVariable long id,
                                               @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.updateUniverse(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<Void> updateTag(@PathVariable long id, @RequestBody String name) {
        nameEntityModifyService.updateTag(id, name);
        return ResponseEntity.ok().build();
    }

}
