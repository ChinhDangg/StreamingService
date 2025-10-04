package dev.chinh.streamingservice.search.service;

import dev.chinh.streamingservice.search.MediaSearchItem;
import dev.chinh.streamingservice.search.MediaSearchRequest;
import dev.chinh.streamingservice.search.MediaSearchResult;
import lombok.RequiredArgsConstructor;
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

        MediaSearchResult result =  openSearchService.advanceSearch(fieldValues, page, size, sortByYear, sortOrder);
        cacheMediaSearchItems(result.getSearchItems());
        return result;
    }

    public MediaSearchResult search(String searchString, int page, int size, boolean sortByYear,
                                    SortOrder sortOrder) throws IOException {

        MediaSearchResult result = openSearchService.search(searchString, page, size, sortByYear, sortOrder);
        cacheMediaSearchItems(result.getSearchItems());
        return result;
    }

    public MediaSearchResult searchByKeywords(String field, Collection<Object> keywords, int page, int size, boolean sortByYear,
                                    SortOrder sortOrder) throws IOException {
        MediaSearchRequest.validateFieldName(field);
        MediaSearchResult result =  openSearchService.searchTermByOneField(field, keywords, page, size, sortByYear, sortOrder);
        cacheMediaSearchItems(result.getSearchItems());
        return result;
    }

    public MediaSearchResult searchMatch(String field, Collection<String> searchStrings, int page, int size, boolean sortByYear,
                                         SortOrder sortOrder) throws IOException {
        MediaSearchRequest.validateFieldName(field);
        MediaSearchResult result = openSearchService.searchMatchByOneField(field, searchStrings, page, size, sortByYear, sortOrder);
        cacheMediaSearchItems(result.getSearchItems());
        return result;
    }
}
