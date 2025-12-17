package dev.chinh.streamingservice.backend.search.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class MediaSearchResult {

    private final List<MediaSearchItemResponse> searchItems;

    // Page info
    private int page;
    private int pageSize;
    private long totalPages;
    private long total;
}
