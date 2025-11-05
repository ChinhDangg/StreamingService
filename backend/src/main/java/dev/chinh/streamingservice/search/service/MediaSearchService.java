package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.*;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OpenSearchService openSearchService;
    private final ObjectMapper mapper;
    private final AlbumService albumService;
    private final MediaMapper mediaMapper;

    public static final Resolution thumbnailResolution = Resolution.p480;

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
                if (!rangeField.getField().equals(ContentMetaData.UPLOAD_DATE) && !rangeField.getField().equals(ContentMetaData.YEAR))
                    throw new IllegalArgumentException("Range query not support for field: " + rangeField.getField());
            }
        }

        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.advanceSearch(includes, excludes, request.getRangeFields(), page, size, sortBy, sortOrder), page, size);

        processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    private List<SearchFieldGroup> mapMediaSearchFieldsToSearchFieldGroups(List<MediaSearchField> searchFields) {
        List<SearchFieldGroup> searchFieldGroups = new ArrayList<>();
        for (MediaSearchField includeField : searchFields) {
            ContentMetaData.validateSearchFieldName(includeField.getField());
            if (includeField.getField().equals(ContentMetaData.TITLE)) {
                searchFieldGroups.add(new SearchFieldGroup(
                        includeField.getField(), includeField.getValues(), includeField.isMustAll(), false
                ));
            } else {
                searchFieldGroups.add(new SearchFieldGroup(
                        includeField.getField(), includeField.getValues(), includeField.isMustAll(), true
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

        processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchByKeywords(String field, Collection<Object> keywords, int page, int size,
                                              SortBy sortBy, SortOrder sortOrder) throws IOException {
        ContentMetaData.validateSearchFieldName(field);
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchTermByOneField(field, keywords, page, size, sortBy, sortOrder), page, size);

        processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    public MediaSearchResult searchMatchAll(int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        MapSearchResult mapSearchResult = mapResponseToMediaSearchResult(
                openSearchService.searchMatchAll(page, size, sortBy, sortOrder), page, size);

        processThumbnails(mapSearchResult.searchItems);
        cacheMediaSearchItems(mapSearchResult.searchItems);
        return mapSearchResult.searchResult;
    }

    private void processThumbnails(Collection<MediaSearchItem> items) {
        long now = System.currentTimeMillis() + 60 * 60 * 1000;
        List<MediaDescription> newThumbnails = new ArrayList<>();
        for (MediaDescription item : items) {
            if (!item.hasThumbnail())
                continue;
            Boolean addedCache = addCacheThumbnails(
                    Paths.get(getThumbnailPath(item.getId(), thumbnailResolution, item.getThumbnail())).getFileName().toString(), now);
            if (addedCache) { // if new thumbnail (not in cache)
                newThumbnails.add(item);
            }
        }
        if (newThumbnails.isEmpty())
            return;
        new Thread(() -> {
            var albumUrlInfo = albumService.getMixThumbnailImagesAsAlbumUrls(newThumbnails, thumbnailResolution);
            try {
                if (albumUrlInfo.mediaUrlList().isEmpty())
                    return;
                albumService.processResizedImagesInBatch(albumUrlInfo, 0, newThumbnails.size(), false);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private Boolean addCacheThumbnails(String thumbnailFileName, long expiry) {
        return redisTemplate.opsForZSet().add("thumbnail-cache", thumbnailFileName, expiry);
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllThumbnailCacheLastAccess(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        redisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
    }

    public static String getThumbnailPath(long albumId, Resolution resolution, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + albumId + "_" + resolution + "." + originalExtension;
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }

    public record MapSearchResult(
            MediaSearchResult searchResult,
            Collection<MediaSearchItem> searchItems
    ) {}

    private MapSearchResult mapResponseToMediaSearchResult(SearchResponse response, int page, int size) {
        List<MediaSearchItem> items = new ArrayList<>();
        List<MediaSearchItemResponse> itemResponses = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            MediaSearchItem searchItem = mapper.convertValue(hit.getSourceAsMap(),  MediaSearchItem.class);
            items.add(searchItem);
            MediaSearchItemResponse itemResponse = mediaMapper.map(searchItem);
            itemResponse.setThumbnail(searchItem.hasThumbnail() ? getThumbnailPath(
                    searchItem.getId(), thumbnailResolution, searchItem.getThumbnail()) : null);
            itemResponse.setMediaType(searchItem.hasKey() ? MediaType.VIDEO : MediaType.IMAGE);
            itemResponses.add(itemResponse);
        }

        MediaSearchResult result = new MediaSearchResult(itemResponses);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotal(Objects.requireNonNull(response.getHits().getTotalHits()).value());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return new MapSearchResult(result, items);
    }
}
