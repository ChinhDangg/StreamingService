package dev.chinh.streamingservice.backend.modify.controller;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.backend.modify.NameAndThumbnailPostRequest;
import dev.chinh.streamingservice.backend.modify.NameEntityDTO;
import dev.chinh.streamingservice.backend.modify.service.NameEntityModifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/modify/name")
public class NameEntityModifyController {

    private final NameEntityModifyService nameEntityModifyService;

    @GetMapping("/authors")
    public ResponseEntity<List<NameEntityDTO>> searchAuthors(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntityModifyService.searchNameContaining(ContentMetaData.AUTHORS, name));
    }

    @GetMapping("/characters")
    public ResponseEntity<List<NameEntityDTO>> searchCharacters(@RequestParam  String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntityModifyService.searchNameContaining(ContentMetaData.CHARACTERS, name));
    }

    @GetMapping("/universes")
    public ResponseEntity<List<NameEntityDTO>> searchUniverses(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntityModifyService.searchNameContaining(ContentMetaData.UNIVERSES, name));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<NameEntityDTO>> searchTags(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntityModifyService.searchNameContaining(ContentMetaData.TAGS, name));
    }


    @PostMapping("/authors")
    public ResponseEntity<Void> addAuthor(@RequestBody String name) {
        nameEntityModifyService.addAuthor(name);
        return ResponseEntity.status(HttpStatus.SC_CREATED).build();
    }

    @PostMapping(value = "/characters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> addCharacter(@Valid @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.addCharacter(request);
        return ResponseEntity.status(HttpStatus.SC_CREATED).build();
    }

    @PostMapping(value = "/universes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> addUniverse(@Valid @ModelAttribute NameAndThumbnailPostRequest request) {
        nameEntityModifyService.addUniverse(request);
        return ResponseEntity.status(HttpStatus.SC_CREATED).build();
    }

    @PostMapping("/tags")
    public ResponseEntity<Void> addTag(@RequestBody String name) {
        nameEntityModifyService.addTag(name);
        return ResponseEntity.status(HttpStatus.SC_CREATED).build();
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
