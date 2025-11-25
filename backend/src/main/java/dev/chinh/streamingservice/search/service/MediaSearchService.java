package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.*;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final ObjectMapper mapper;
    private final MediaMapper mediaMapper;
    private final OpenSearchService openSearchService;
    private final MediaMetadataService mediaMetadataService;
    private final ThumbnailService thumbnailService;

    record NameEntry(String name) {}

    public List<String> searchContaining(String index, String text) throws IOException {
        ContentMetaData.validateNameText(text);
        SearchResponse<NameEntry> response = openSearchService.searchContaining(
                index, ContentMetaData.NAME, text, NameEntry.class);
        return mapSearchReponseNameEntryToList(response);
    }

    private List<String> mapSearchReponseNameEntryToList(SearchResponse<NameEntry> response) {
        return response.hits().hits().stream()
                .map(h -> {
                    assert h.source() != null;
                    return h.source().name;
                })
                .toList();
    }

    private void cacheMediaSearchItems(Collection<MediaSearchItem> items) {
        for (MediaSearchItem item : items) {
            mediaMetadataService.cacheMediaSearchItem(item, Duration.ofMinutes(15));
        }
    }

    public MediaSearchResult advanceSearch(MediaSearchRequest request, int page, int size,
                                           SortBy sortBy, SortOrder sortOrder) throws IOException {

        boolean hasAnyField = request.hasAny();
        if (!hasAnyField) {
            throw new BadRequestException("Empty advanced search request");
        }

        List<SearchFieldGroup> includes = request.getIncludeFields() == null ? null :
                mapMediaSearchFieldsToSearchFieldGroups(request.getIncludeFields());
        List<SearchFieldGroup> excludes = request.getExcludeFields() == null ? null :
                mapMediaSearchFieldsToSearchFieldGroups(request.getExcludeFields());

        if (request.getRangeFields() != null) {
            for (MediaSearchRangeField rangeField : request.getRangeFields()) {
                ContentMetaData.validateSearchRangeFieldName(rangeField.getField());
            }
        }

        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.advanceSearch(includes, excludes, request.getRangeFields(), page, size, sortBy, sortOrder), page, size);

        thumbnailService.processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    private List<SearchFieldGroup> mapMediaSearchFieldsToSearchFieldGroups(List<MediaSearchField> searchFields) {
        List<SearchFieldGroup> searchFieldGroups = new ArrayList<>();
        for (MediaSearchField includeField : searchFields) {
            ContentMetaData.validateSearchFieldName(includeField.getField());
            if (includeField.getField().equals(ContentMetaData.TITLE)) {
                searchFieldGroups.add(new SearchFieldGroup(
                        includeField.getField(), includeField.getValues(), includeField.isMatchAll(), false
                ));
            } else {
                searchFieldGroups.add(new SearchFieldGroup(
                        includeField.getField(), includeField.getValues(), includeField.isMatchAll(), true
                ));
            }
        }
        return searchFieldGroups;
    }

    public MediaSearchResult search(String searchString, int page, int size, SortBy sortBy,
                                    SortOrder sortOrder) throws IOException {

        if (!MediaSearchField.validateSearchString(searchString)) {
            throw new IllegalArgumentException("Invalid search string");
        }
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.search(searchString, page, size, sortBy, sortOrder), page, size);

        thumbnailService.processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchByKeywords(String field, List<Object> keywords, boolean matchAll, int page, int size,
                                              SortBy sortBy, SortOrder sortOrder) throws IOException {
        ContentMetaData.validateSearchFieldName(field);
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchTermByOneField(field, keywords, matchAll, page, size, sortBy, sortOrder), page, size);

        thumbnailService.processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchMatchAll(int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchMatchAll(page, size, sortBy, sortOrder), page, size);

        thumbnailService.processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public record MapSearchResult(
            MediaSearchResult searchResult,
            Collection<MediaSearchItem> searchItems
    ) {}

    private MapSearchResult mapResponseToMediaSearchResult(SearchResponse<Object> response, int page, int size) {
        List<MediaSearchItem> items = new ArrayList<>();
        List<MediaSearchItemResponse> itemResponses = new ArrayList<>();
        for (Hit<Object> hit : response.hits().hits()) {
            MediaSearchItem searchItem = mapper.convertValue(hit.source(), MediaSearchItem.class);
            items.add(searchItem);
            MediaSearchItemResponse itemResponse = mediaMapper.map(searchItem);
            itemResponse.setThumbnail(searchItem.hasThumbnail() ? ThumbnailService.getThumbnailPath(
                    searchItem.getId(), searchItem.getThumbnail()) : null);
            itemResponse.setMediaType(searchItem.hasKey() ? MediaType.VIDEO : searchItem.isGrouper() ? MediaType.GROUPER : MediaType.IMAGE);
            itemResponses.add(itemResponse);
        }

        MediaSearchResult result = new MediaSearchResult(itemResponses);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotal(response.hits().hits().size());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return new MapSearchResult(result, items);
    }
}
