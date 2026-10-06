package dev.chinh.streamingservice.search.controller;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.MediaSearchRequest;
import dev.chinh.streamingservice.search.data.MediaSearchResult;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/search/media")
public class MediaSearchController {

    private final String ORIGINAL_SETTING_HEADER = "X-Original-Resolution";
    private final int pageSize = 20;

    private final MediaSearchService mediaSearchService;

    @PostMapping
    public ResponseEntity<MediaSearchResult> search(@RequestParam String searchString,
                                                    @RequestParam(required = false) int page,
                                                    @RequestParam(required = false) SortBy sortBy,
                                                    @RequestParam(required = false) SortOrder sortOrder,
                                                    HttpServletRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) throws Exception {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(mediaSearchService.search(jwt.getSubject(), searchString, page, pageSize, sortBy, sortOrder, originalResolution));
    }

    @PostMapping("/advance")
    public ResponseEntity<MediaSearchResult> advanceSearch(@RequestBody MediaSearchRequest mediaSearchRequest,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder,
                                                           HttpServletRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) throws Exception {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(mediaSearchService.advanceSearch(jwt.getSubject(), mediaSearchRequest, page, pageSize, sortBy, sortOrder, originalResolution));
    }

    @PostMapping("/keyword")
    public ResponseEntity<MediaSearchResult> keywordSearch(@RequestParam(name = "field") MediaNameEntityConstant nameEntity,
                                                           @RequestParam(name = "keys") List<Object> keywordList,
                                                           @RequestParam(required = false, defaultValue = "true") boolean matchAll,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder,
                                                           HttpServletRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) throws Exception {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(
                mediaSearchService.searchByKeywords(jwt.getSubject(), nameEntity.getName(), keywordList, matchAll, page, pageSize, sortBy, sortOrder, originalResolution));
    }

    @PostMapping("/match-all")
    public ResponseEntity<MediaSearchResult> matchAllSearch(@RequestParam(required = false) int page,
                                                            @RequestParam(required = false) SortBy sortBy,
                                                            @RequestParam(required = false) SortOrder sortOrder,
                                                            HttpServletRequest request,
                                                            @AuthenticationPrincipal Jwt jwt) throws Exception {
        boolean originalResolution = request.getHeader(ORIGINAL_SETTING_HEADER) != null;
        return ResponseEntity.ok().body(mediaSearchService.searchMatchAll(jwt.getSubject(), page, pageSize, sortBy, sortOrder, originalResolution));
    }
}
