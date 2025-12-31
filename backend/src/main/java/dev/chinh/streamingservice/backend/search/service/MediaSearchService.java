package dev.chinh.streamingservice.backend.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.backend.content.service.ThumbnailService;
import dev.chinh.streamingservice.persistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.searchclient.OpenSearchService;
import dev.chinh.streamingservice.backend.search.data.*;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import dev.chinh.streamingservice.searchclient.data.MediaSearchRangeField;
import dev.chinh.streamingservice.searchclient.data.SearchFieldGroup;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
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
    private final MediaSearchCacheService mediaSearchCacheService;
    private final ThumbnailService thumbnailService;

    public static final String MEDIA_INDEX_NAME = "media";
    private final MinIOService minIOService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    record NameEntry(String name) {}

    public List<String> searchContaining(String index, String text) throws IOException {
        ContentMetaData.validateSearchText(text);
        SearchResponse<NameEntry> response = searchContaining(
                index, ContentMetaData.NAME, text, NameEntry.class);
        return mapSearchReponseNameEntryToList(response);
    }

    public <T> SearchResponse<T> searchContaining(String index, String field, String text, Class<T> clazz) throws IOException {
        return openSearchService.searchContaining(index, field, text, clazz);
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
            mediaSearchCacheService.cacheMediaSearchItem(item, Duration.ofMinutes(15));
        }
    }

    public MediaSearchResult advanceSearch(MediaSearchRequest request, int page, int size,
                                           SortBy sortBy, SortOrder sortOrder) throws Exception {

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
                openSearchService.advanceSearch(MEDIA_INDEX_NAME, includes, excludes, request.getRangeFields(), page, size, sortBy, sortOrder),
                page, size);

        if (!Boolean.parseBoolean(alwaysShowOriginalResolution))
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
                                    SortOrder sortOrder) throws Exception {

        if (!MediaSearchField.validateSearchString(searchString)) {
            throw new IllegalArgumentException("Invalid search string");
        }
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.search(MEDIA_INDEX_NAME, searchString, page, size, sortBy, sortOrder),
                page, size);

        if (!Boolean.parseBoolean(alwaysShowOriginalResolution))
            thumbnailService.processThumbnails(mapSearchResult.searchItems);

        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchByKeywords(String field, List<Object> keywords, boolean matchAll, int page, int size,
                                              SortBy sortBy, SortOrder sortOrder) throws Exception {
        ContentMetaData.validateSearchFieldName(field);
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchTermByOneField(MEDIA_INDEX_NAME, field, keywords, matchAll, page, size, sortBy, sortOrder),
                page, size);

        if (!Boolean.parseBoolean(alwaysShowOriginalResolution))
            thumbnailService.processThumbnails(mapSearchResult.searchItems);

        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchMatchAll(int page, int size, SortBy sortBy, SortOrder sortOrder) throws Exception {
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchMatchAll(MEDIA_INDEX_NAME, page, size, sortBy, sortOrder), page, size);

        if (!Boolean.parseBoolean(alwaysShowOriginalResolution))
            thumbnailService.processThumbnails(mapSearchResult.searchItems);

        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public record MapSearchResult(
            MediaSearchResult searchResult,
            Collection<MediaSearchItem> searchItems
    ) {}

    private MapSearchResult mapResponseToMediaSearchResult(SearchResponse<Object> response, int page, int size) throws Exception {
        List<MediaSearchItem> items = new ArrayList<>();
        List<MediaSearchItemResponse> itemResponses = new ArrayList<>();
        for (Hit<Object> hit : response.hits().hits()) {
            MediaSearchItem searchItem = mapper.convertValue(hit.source(), MediaSearchItem.class);
            items.add(searchItem);
            MediaSearchItemResponse itemResponse = mediaMapper.map(searchItem);
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                String thumbnailBucket = searchItem.getMediaType() == MediaType.ALBUM ? searchItem.getBucket() : ContentMetaData.THUMBNAIL_BUCKET;
                itemResponse.setThumbnail(searchItem.hasThumbnail()
                        ? minIOService.getObjectUrl(thumbnailBucket, searchItem.getThumbnail())
                        : null);
            } else {
                itemResponse.setThumbnail(searchItem.hasThumbnail() ? ThumbnailService.getThumbnailPath(
                        searchItem.getId(), searchItem.getThumbnail()) : null);
            }
            itemResponse.setMediaType(searchItem.getMediaType());
            itemResponses.add(itemResponse);
        }

        MediaSearchResult result = new MediaSearchResult(itemResponses);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotal(response.hits().total() == null ? 0 : response.hits().total().value());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return new MapSearchResult(result, items);
    }
}
