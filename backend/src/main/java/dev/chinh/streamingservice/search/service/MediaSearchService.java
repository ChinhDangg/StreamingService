package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.*;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
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
    private final ObjectMapper mapper;
    private final MediaMapper mediaMapper;
    private final OpenSearchService openSearchService;
    private final AlbumService albumService;
    private final MediaMetadataService mediaMetadataService;

    public static final Resolution thumbnailResolution = Resolution.p480;

    record NameEntry(String name) {}

    public List<String> searchAuthorContaining(String text) throws IOException {
        return searchContaining(ContentMetaData.AUTHORS, ContentMetaData.NAME, text);
    }

    public List<String> searchCharacterContaining(String text) throws IOException {
        return searchContaining(ContentMetaData.CHARACTERS, ContentMetaData.NAME, text);
    }

    public List<String> searchUniverseContaining(String text) throws IOException {
        return searchContaining(ContentMetaData.UNIVERSES, ContentMetaData.NAME, text);
    }

    public List<String> searchTagContaining(String text) throws IOException {
        return searchContaining(ContentMetaData.TAGS, ContentMetaData.NAME, text);
    }

    private List<String> searchContaining(String index, String field, String text) throws IOException {
        ContentMetaData.validateNameText(text);
        SearchResponse<NameEntry> response = openSearchService.searchContaining(
                index, field, text, NameEntry.class);
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

    public MediaSearchResult searchByKeywords(String field, List<Object> keywords, int page, int size,
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
        var albumUrlInfo = getMixThumbnailImagesAsAlbumUrls(newThumbnails, thumbnailResolution);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            albumService.processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, false);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AlbumService.AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList, Resolution resolution) {
        List<String> pathList = new ArrayList<>();
        List<AlbumService.MediaUrl> albumUrlList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            bucketList.add(mediaDescription.getBucket());
            pathList.add(mediaDescription.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaDescription.getId(), resolution, mediaDescription.getThumbnail());
            albumUrlList.add(new AlbumService.MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumService.AlbumUrlInfo(albumUrlList, bucketList, null, pathList);
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

    private MapSearchResult mapResponseToMediaSearchResult(SearchResponse<Object> response, int page, int size) {
        List<MediaSearchItem> items = new ArrayList<>();
        List<MediaSearchItemResponse> itemResponses = new ArrayList<>();
        for (Hit<Object> hit : response.hits().hits()) {
            MediaSearchItem searchItem = mapper.convertValue(hit.source(),  MediaSearchItem.class);
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
        result.setTotal(response.hits().hits().size());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return new MapSearchResult(result, items);
    }
}
