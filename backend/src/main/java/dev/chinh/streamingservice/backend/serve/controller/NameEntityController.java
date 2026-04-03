package dev.chinh.streamingservice.backend.serve.controller;

import dev.chinh.streamingservice.backend.serve.service.MediaNameEntityService;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/name")
public class NameEntityController {

    private final MediaNameEntityService mediaNameEntityService;

    @GetMapping("/authors")
    public ResponseEntity<?> getAuthors(@RequestParam(value = "p", defaultValue = "0") int offset,
                                        @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                        @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order,
                                        @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllAuthors(jwt.getSubject(), offset, sortBy, order));
    }

    @GetMapping("/characters")
    public ResponseEntity<?> getCharacters(@RequestParam(value = "p", defaultValue = "0") int offset,
                                           @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                           @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order,
                                           @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllCharacters(jwt.getSubject(), offset, sortBy, order));
    }

    @GetMapping("/universes")
    public ResponseEntity<?> getUniverses(@RequestParam(value = "p", defaultValue = "0") int offset,
                                          @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                          @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order,
                                          @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllUniverses(jwt.getSubject(), offset, sortBy, order));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getTags(@RequestParam(value = "p", defaultValue = "0") int offset,
                                     @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                     @RequestParam(value = "order", defaultValue = "ASC") Sort.Direction order,
                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaNameEntityService.findAllTags(jwt.getSubject(), offset, sortBy, order));
    }
}
