package dev.chinh.streamingservice.backend.serve.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.backend.content.service.ThumbnailService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.searchclient.OpenSearchService;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaNameEntityService {

    private final ThumbnailService thumbnailService;
    private final MinIOService minIOService;
    private final OpenSearchService openSearchService;
    private final ObjectMapper objectMapper;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;
    private final int pageSize = 20;

    public Page<NameEntityDTO> findAllAuthors(String userId, int page, SortBy sortBy, SortOrder sortOrder) {
        return mapInfo(userId, page, searchMatchAll(ContentMetaData.AUTHORS, userId, page, sortBy, sortOrder), false);
    }

    public Page<NameEntityDTO> findAllCharacters(String userId, int page, SortBy sortBy, SortOrder sortOrder) {
        return mapInfo(userId, page, searchMatchAll(ContentMetaData.CHARACTERS, userId, page, sortBy, sortOrder), true);
    }

    public Page<NameEntityDTO> findAllUniverses(String userId, int page, SortBy sortBy, SortOrder sortOrder) {
        return mapInfo(userId, page, searchMatchAll(ContentMetaData.UNIVERSES, userId, page, sortBy, sortOrder), true);
    }

    public Page<NameEntityDTO> findAllTags(String userId, int page, SortBy sortBy, SortOrder sortOrder) {
        return mapInfo(userId, page, searchMatchAll(ContentMetaData.TAGS, userId, page, sortBy, sortOrder), false);
    }

    private Page<NameEntityDTO> mapInfo(String userId, int page, SearchResponse<Object> searchResponse, boolean hasThumbnail) {
        int size = searchResponse.hits().hits().size();
        if (size == 0)
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, pageSize), 0);

        List<NameEntityDTO> nameEntries = new ArrayList<>(size);
        for (Hit<Object> hit : searchResponse.hits().hits()) {
            NameEntityDTO nameEntry = objectMapper.convertValue(hit.source(), NameEntityDTO.class);
            nameEntries.add(nameEntry);
        }
        if (hasThumbnail) {
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(minIOService.getObjectUrl(ContentMetaData.THUMBNAIL_BUCKET, ContentMetaData.removeUserIdDirFromObjectKey(userId, nameEntry.getThumbnail())));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                thumbnailService.processThumbnails(userId, nameEntries); // process thumbnails first with the original thumbnail path
                // set thumbnail path to the directory of the thumbnail location
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(ThumbnailService.getThumbnailPath(ThumbnailService.getThumbnailUrlParentPath(), nameEntry.getName(), nameEntry.getThumbnail()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        int total = searchResponse.hits().total() == null ? 0 : (int) searchResponse.hits().total().value();
        return new PageImpl<>(nameEntries, PageRequest.of(page, pageSize), total);
    }

    public SearchResponse<Object> searchMatchAll(String indexName, String userId, int page, SortBy sortBy, SortOrder sortOrder) {
        if (page > 50)
            throw new IllegalArgumentException("Max page's'exceeded, please refine your search");
        try {
            return openSearchService.searchMatchAll(indexName, Long.parseLong(userId), page, pageSize, sortBy, sortOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
