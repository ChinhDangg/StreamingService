package dev.chinh.streamingservice.backend.search.service;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NameEntitySearchService {

    private final MediaSearchService mediaSearchService;

    public List<NameEntityDTO> searchNameContaining(String index, String name) throws IOException {
        SearchResponse<NameEntityDTO> response = mediaSearchService.searchContaining(index, ContentMetaData.NAME, name, NameEntityDTO.class);
        return response.hits().hits().stream()
                .map(h -> {
                    NameEntityDTO dto = h.source();
                    assert dto != null;
                    assert h.id() != null;
                    dto.setId(Long.parseLong(h.id()));
                    return dto;
                })
                .toList();
    }
}
