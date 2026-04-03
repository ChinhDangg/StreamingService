package dev.chinh.streamingservice.backend.search.controller;

import dev.chinh.streamingservice.backend.search.data.MediaSearchRequest;
import dev.chinh.streamingservice.backend.search.data.MediaSearchResult;
import dev.chinh.streamingservice.backend.search.service.MediaSearchService;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MediaSearchService mediaSearchService;
    private final int pageSize = 20;

    @PostMapping
    public ResponseEntity<MediaSearchResult> search(@RequestParam String searchString,
                                                    @RequestParam(required = false) int page,
                                                    @RequestParam(required = false) SortBy sortBy,
                                                    @RequestParam(required = false) SortOrder sortOrder,
                                                    @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok().body(mediaSearchService.search(jwt.getSubject(), searchString, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/advance")
    public ResponseEntity<MediaSearchResult> advanceSearch(@RequestBody MediaSearchRequest mediaSearchRequest,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder,
                                                           @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok().body(mediaSearchService.advanceSearch(jwt.getSubject(), mediaSearchRequest, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/keyword")
    public ResponseEntity<MediaSearchResult> keywordSearch(@RequestParam(name = "field") MediaNameEntityConstant nameEntity,
                                                           @RequestParam(name = "keys") List<Object> keywordList,
                                                           @RequestParam(required = false, defaultValue = "true") boolean matchAll,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder,
                                                           @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok().body(
                mediaSearchService.searchByKeywords(jwt.getSubject(), nameEntity.getName(), keywordList, matchAll, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/match-all")
    public ResponseEntity<MediaSearchResult> matchAllSearch(@RequestParam(required = false) int page,
                                                            @RequestParam(required = false) SortBy sortBy,
                                                            @RequestParam(required = false) SortOrder sortOrder,
                                                            @AuthenticationPrincipal Jwt jwt) throws Exception {
        return ResponseEntity.ok().body(mediaSearchService.searchMatchAll(jwt.getSubject(), page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/name/{nameEntity}")
    public List<?> searchNameEntity(@PathVariable MediaNameEntityConstant nameEntity,
                                    @RequestParam(name = "s") String authorSearchString,
                                    @AuthenticationPrincipal Jwt jwt) throws IOException {
        return mediaSearchService.searchContaining(jwt.getSubject(), nameEntity.getName(), authorSearchString);
    }
}
