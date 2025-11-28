package dev.chinh.streamingservice.search.controller;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.MediaSearchRequest;
import dev.chinh.streamingservice.search.data.MediaSearchResult;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import dev.chinh.streamingservice.upload.MediaNameEntityConstant;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.http.ResponseEntity;
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
                                                    @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(mediaSearchService.search(searchString, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/advance")
    public ResponseEntity<MediaSearchResult> advanceSearch(@RequestBody MediaSearchRequest mediaSearchRequest,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(mediaSearchService.advanceSearch(mediaSearchRequest, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/keyword")
    public ResponseEntity<MediaSearchResult> keywordSearch(@RequestParam(name = "field") MediaNameEntityConstant nameEntity,
                                                           @RequestParam(name = "keys") List<Object> keywordList,
                                                           @RequestParam(required = false, defaultValue = "true") boolean matchAll,
                                                           @RequestParam(required = false) int page,
                                                           @RequestParam(required = false) SortBy sortBy,
                                                           @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(
                mediaSearchService.searchByKeywords(nameEntity.getName(), keywordList, matchAll, page, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/match-all")
    public ResponseEntity<MediaSearchResult> matchAllSearch(@RequestParam(required = false) int page,
                                                         @RequestParam(required = false) SortBy sortBy,
                                                         @RequestParam(required = false) SortOrder sortOrder) throws IOException {
        return ResponseEntity.ok().body(mediaSearchService.searchMatchAll(page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/suggestion/authors")
    public List<String> getAuthorList(@RequestParam(name = "s") String authorSearchString) throws IOException {
        return mediaSearchService.searchContaining(ContentMetaData.AUTHORS, authorSearchString);
    }

    @GetMapping("/suggestion/characters")
    public List<String> getCharacterList(@RequestParam(name = "s") String characterSearchString) throws IOException {
        return mediaSearchService.searchContaining(ContentMetaData.CHARACTERS, characterSearchString);
    }

    @GetMapping("/suggestion/universes")
    public List<String> getUniverseList(@RequestParam(name = "s") String universeSearchString) throws IOException {
        return mediaSearchService.searchContaining(ContentMetaData.UNIVERSES, universeSearchString);
    }

    @GetMapping("/suggestion/tags")
    public List<String> getTagList(@RequestParam(name = "s") String tagSearchString) throws IOException {
        return mediaSearchService.searchContaining(ContentMetaData.TAGS, tagSearchString);
    }
}
