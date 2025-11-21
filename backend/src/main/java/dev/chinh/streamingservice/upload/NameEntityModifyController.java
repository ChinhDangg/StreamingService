package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.data.ContentMetaData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media/modify")
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

    @PostMapping("/characters")
    public ResponseEntity<Void> addCharacter(@Valid @RequestBody NameEntityModifyService.NameAndThumbnailPostRequest request) {
        nameEntityModifyService.addCharacter(request);
        return ResponseEntity.status(HttpStatus.SC_CREATED).build();
    }

    @PostMapping("/universes")
    public ResponseEntity<Void> addUniverse(@Valid @RequestBody NameEntityModifyService.NameAndThumbnailPostRequest request) {
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

    @PutMapping("/characters/{id}")
    public ResponseEntity<Void> updateCharacter(@PathVariable long id,
                                                @RequestBody NameEntityModifyService.NameAndThumbnailPostRequest request) {
        nameEntityModifyService.updateCharacter(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/universes/{id}")
    public ResponseEntity<Void> updateUniverse(@PathVariable long id,
                                               @RequestBody NameEntityModifyService.NameAndThumbnailPostRequest request) {
        nameEntityModifyService.updateUniverse(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<Void> updateTag(@PathVariable long id, @RequestBody String name) {
        nameEntityModifyService.updateTag(id, name);
        return ResponseEntity.ok().build();
    }

}
