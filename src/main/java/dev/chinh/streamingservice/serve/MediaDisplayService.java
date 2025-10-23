package dev.chinh.streamingservice.serve;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final AlbumService albumService;
    private final ObjectMapper objectMapper;

    public MediaDisplayContent getMediaContentInfo(long mediaId) {
        MediaSearchItem mediaItem = (MediaSearchItem) albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = objectMapper.convertValue(mediaItem, MediaDisplayContent.class);
        if (mediaItem.hasThumbnail())
            mediaDisplayContent.setThumbnail(MediaSearchService.getThumbnailPath(mediaId, MediaSearchService.thumbnailResolution, mediaItem.getThumbnail()));
        return mediaDisplayContent;
    }

}
