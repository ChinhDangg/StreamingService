package dev.chinh.streamingservice.search.controller;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.service.NameEntityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search/name")
public class NameEntitySearchController {

    private final String ORIGINAL_SETTING_HEADER = "X-Original-Resolution";

    private final NameEntityService nameEntityService;

    @GetMapping("/authors")
    public ResponseEntity<?> getAuthors(@RequestParam(value = "p", defaultValue = "0") int offset,
                                        @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                        @RequestParam(value = "order", defaultValue = "Asc") SortOrder order,
                                        HttpServletRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(nameEntityService.findAllAuthors(jwt.getSubject(), offset, sortBy, order, originalResolution));
    }

    @GetMapping("/characters")
    public ResponseEntity<?> getCharacters(@RequestParam(value = "p", defaultValue = "0") int offset,
                                           @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                           @RequestParam(value = "order", defaultValue = "Asc") SortOrder order,
                                           HttpServletRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(nameEntityService.findAllCharacters(jwt.getSubject(), offset, sortBy, order, originalResolution));
    }

    @GetMapping("/universes")
    public ResponseEntity<?> getUniverses(@RequestParam(value = "p", defaultValue = "0") int offset,
                                          @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                          @RequestParam(value = "order", defaultValue = "Asc") SortOrder order,
                                          HttpServletRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(nameEntityService.findAllUniverses(jwt.getSubject(), offset, sortBy, order, originalResolution));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getTags(@RequestParam(value = "p", defaultValue = "0") int offset,
                                     @RequestParam(value = "by", defaultValue = "NAME") SortBy sortBy,
                                     @RequestParam(value = "order", defaultValue = "Asc") SortOrder order,
                                     HttpServletRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(nameEntityService.findAllTags(jwt.getSubject(), offset, sortBy, order, originalResolution));
    }

    @GetMapping("/{nameEntity}")
    public List<?> searchNameEntity(@PathVariable MediaNameEntityConstant nameEntity,
                                    @RequestParam(name = "s") String nameSearchString,
                                    @AuthenticationPrincipal Jwt jwt) throws IOException {
        return nameEntityService.searchContaining(jwt.getSubject(), nameEntity.getName(), nameSearchString);
    }
}
