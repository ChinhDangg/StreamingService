package dev.chinh.streamingservice.search.controller;

import dev.chinh.streamingservice.search.data.MediaSearchRequest;
import dev.chinh.streamingservice.search.data.MediaSearchResult;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.opensearch.search.sort.SortOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MediaSearchService mediaSearchService;
    private final int pageSize = 20;

    public record MediaFieldSearchRequest(
            @NotNull @NotBlank
            String field,
            @NotNull
            Collection<Object> text
    ) {}

    @PostMapping
    public ResponseEntity<MediaSearchResult> search(@RequestParam String searchString,
                                                    @RequestParam(required = false) int page,
                                                    @RequestParam(required = false) boolean sortByYear,
                                                    @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(mediaSearchService.search(searchString, page, pageSize, sortByYear, sortOrder));
    }

    @PostMapping("/advance")
    public ResponseEntity<MediaSearchResult> advanceSearch(@RequestBody MediaSearchRequest mediaSearchRequest,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) boolean sortByYear,
                                                           @RequestParam(required = false) SortOrder sortOrder) throws IOException, IllegalAccessException {
        return ResponseEntity.ok().body(mediaSearchService.advanceSearch(mediaSearchRequest, page, pageSize, sortByYear, sortOrder));
    }

    @PostMapping("/keyword")
    public ResponseEntity<MediaSearchResult> keywordSearch(@Valid @RequestBody MediaFieldSearchRequest searchRequest,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) boolean sortByYear,
                                                           @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(
                mediaSearchService.searchByKeywords(searchRequest.field,  searchRequest.text, page, pageSize, sortByYear, sortOrder));
    }

    @PostMapping("/match")
    public ResponseEntity<MediaSearchResult> matchSearch(@Valid @RequestBody MediaFieldSearchRequest searchRequest,
                                                         @RequestParam(required = false) int page,
                                                         @RequestParam(required = false) boolean sortByYear,
                                                         @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(
                mediaSearchService.searchMatch(searchRequest.field, searchRequest.text, page, pageSize, sortByYear, sortOrder));
    }
}
