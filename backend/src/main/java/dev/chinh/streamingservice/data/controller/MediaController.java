package dev.chinh.streamingservice.data.controller;

import dev.chinh.streamingservice.data.service.MediaNameEntityService;
import dev.chinh.streamingservice.search.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaController {

    private final MediaNameEntityService mediaNameEntityService;

    @GetMapping("/authors")
    public ResponseEntity<?> getAuthors(@RequestParam(value = "p", defaultValue = "0") int offset,
                                        @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                        @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllAuthors(offset, sortBy, order));
    }

    @GetMapping("/characters")
    public ResponseEntity<?> getCharacters(@RequestParam(value = "p", defaultValue = "0") int offset,
                                           @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                            @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllCharacters(offset, sortBy, order));
    }

    @GetMapping("/universes")
    public ResponseEntity<?> getUniverses(@RequestParam(value = "p", defaultValue = "0") int offset,
                                          @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                            @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllUniverses(offset, sortBy, order));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getTags(@RequestParam(value = "p", defaultValue = "0") int offset,
                                     @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                     @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllTags(offset, sortBy, order));
    }
}
