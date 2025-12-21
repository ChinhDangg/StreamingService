package dev.chinh.streamingservice.backend.search.controller;

import dev.chinh.streamingservice.backend.search.service.NameEntitySearchService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search/name")
public class NameEntitySearchController {

    // for modifying page searching with id of name entity included

    private final NameEntitySearchService nameEntitySearchService;

    @GetMapping("/authors")
    public ResponseEntity<List<NameEntityDTO>> searchAuthors(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntitySearchService.searchNameContaining(ContentMetaData.AUTHORS, name));
    }

    @GetMapping("/characters")
    public ResponseEntity<List<NameEntityDTO>> searchCharacters(@RequestParam  String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntitySearchService.searchNameContaining(ContentMetaData.CHARACTERS, name));
    }

    @GetMapping("/universes")
    public ResponseEntity<List<NameEntityDTO>> searchUniverses(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntitySearchService.searchNameContaining(ContentMetaData.UNIVERSES, name));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<NameEntityDTO>> searchTags(@RequestParam String name) throws IOException {
        return ResponseEntity.ok().body(
                nameEntitySearchService.searchNameContaining(ContentMetaData.TAGS, name));
    }
}
