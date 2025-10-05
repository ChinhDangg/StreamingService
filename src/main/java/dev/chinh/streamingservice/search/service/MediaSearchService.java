package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.search.data.MediaSearchRequest;
import dev.chinh.streamingservice.search.data.MediaSearchResult;
import lombok.RequiredArgsConstructor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OpenSearchService openSearchService;
    private final ObjectMapper mapper;

    private void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, Duration.ofHours(1));
    }

    private void cacheMediaSearchItems(Collection<MediaSearchItem> items) {
        for (MediaSearchItem item : items) {
            cacheMediaSearchItem(item);
        }
    }

    public MediaSearchResult advanceSearch(MediaSearchRequest request, int page, int size,
                                           boolean sortByYear, SortOrder sortOrder) throws IllegalAccessException, IOException {
        request.validate();

        Map<String, Collection<Object>> fieldValues = new HashMap<>();
        Field[] fields = MediaSearchRequest.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            Object value = field.get(request);

            if (value == null) continue;

            if (value instanceof Collection<?>) {
                fieldValues.put(fieldName, new ArrayList<>((Collection<?>) value));
            } else {
                fieldValues.put(fieldName, Collections.singletonList(value));
            }
        }
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.advanceSearch(fieldValues, page, size, sortByYear, sortOrder), page, size);

        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult search(String searchString, int page, int size, boolean sortByYear,
                                    SortOrder sortOrder) throws IOException {

        if (!MediaSearchRequest.validateSearchString(searchString)) {
            throw new IllegalArgumentException("Invalid search string");
        }
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.search(searchString, page, size, sortByYear, sortOrder), page, size);

        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchByKeywords(String field, Collection<Object> keywords, int page, int size, boolean sortByYear,
                                    SortOrder sortOrder) throws IOException {
        MediaSearchRequest.validateFieldName(field);
        MapSearchResult mapResult = mapResponseToMediaSearchResult(
                openSearchService.searchTermByOneField(field, keywords, page, size, sortByYear, sortOrder), page, size);
        cacheMediaSearchItems(mapResult.searchItems);
        return mapResult.searchResult;
    }

    public MediaSearchResult searchMatch(String field, Collection<Object> searchStrings, int page, int size, boolean sortByYear,
                                         SortOrder sortOrder) throws IOException {
        MediaSearchRequest.validateFieldName(field);
        MapSearchResult mapResult = mapResponseToMediaSearchResult(
                openSearchService.searchMatchByOneField(field, searchStrings, page, size, sortByYear, sortOrder), page, size);
        cacheMediaSearchItems(mapResult.searchItems);
        return mapResult.searchResult;
    }

    public record MapSearchResult(
            MediaSearchResult searchResult,
            Collection<MediaSearchItem> searchItems
    ) {}

    private MapSearchResult mapResponseToMediaSearchResult(SearchResponse response, int page, int size) {
        List<MediaSearchItem> items = new ArrayList<>();
        List<MediaSearchItemResponse> itemResponses = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            items.add(mapper.convertValue(hit.getSourceAsMap(), MediaSearchItem.class));
            itemResponses.add(mapper.convertValue(hit.getSourceAsMap(), MediaSearchItemResponse.class));
        }

        MediaSearchResult result = new MediaSearchResult(itemResponses);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotal(Objects.requireNonNull(response.getHits().getTotalHits()).value());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return new MapSearchResult(result, items);
    }
}
