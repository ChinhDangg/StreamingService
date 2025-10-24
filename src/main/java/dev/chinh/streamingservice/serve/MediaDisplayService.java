package dev.chinh.streamingservice.serve;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final AlbumService albumService;
    private final ObjectMapper objectMapper;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final int maxBatchSize = 10;

    public MediaDisplayContent getMediaContentInfo(long mediaId) {
        MediaSearchItem mediaItem = (MediaSearchItem) albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = objectMapper.convertValue(mediaItem, MediaDisplayContent.class);
        if (mediaItem.hasThumbnail())
            mediaDisplayContent.setThumbnail(MediaSearchService.getThumbnailPath(mediaId, MediaSearchService.thumbnailResolution, mediaItem.getThumbnail()));

        if (mediaItem.isGrouper()) {
            Pageable pageable = PageRequest.of(0, maxBatchSize);
            Slice<Long> mediaIds = mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaId, pageable);
            mediaDisplayContent.setChildMediaIds(mediaIds);
        }
        return mediaDisplayContent;
    }

    public Slice<Long> getNextGroupOfMedia() {
        return null;
    }

}
