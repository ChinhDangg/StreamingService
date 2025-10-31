package dev.chinh.streamingservice.serve.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import dev.chinh.streamingservice.serve.data.MediaDisplayContent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final AlbumService albumService;
    private final MediaMapper mediaMapper;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final int maxBatchSize = 10;

    public MediaDisplayContent getMediaContentInfo(long mediaId) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        MediaDisplayContent mediaDisplayContent = mediaMapper.map(mediaItem);
        if (mediaItem.hasThumbnail())
            mediaDisplayContent.setThumbnail(MediaSearchService.getThumbnailPath(mediaId, MediaSearchService.thumbnailResolution, mediaItem.getThumbnail()));

        if (mediaItem.isGrouper()) {
            Pageable pageable = PageRequest.of(0, maxBatchSize);
            Slice<Long> mediaIds = mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaId, pageable);
            mediaDisplayContent.setChildMediaIds(mediaIds);
        }
        return mediaDisplayContent;
    }

    public Slice<Long> getNextGroupOfMedia(long mediaId, int offset) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);
        if (!mediaItem.isGrouper()) {
            throw new ResourceNotFoundException("No media grouper found with id: " + mediaId);
        }

        Pageable pageable = PageRequest.of(offset, maxBatchSize);
        return mediaGroupMetaDataRepository.findMediaMetadataIdsByGrouperMetaDataId(mediaId, pageable);
    }

    public void getServePageTypeFromMedia(long mediaId) {
        MediaDescription mediaItem = albumService.getMediaDescriptionGeneral(mediaId);

        // get media display content to display extra info about album media

        if (mediaItem.isGrouper()) {
            System.out.println("Grouper page");
            // display media info content
            // should display link to other inner media if none that raise alert
        } else if (!mediaItem.hasKey()) {
            System.out.println("Album page");
            // call GET http://localhost/api/album/2/p1080 to get all media in album with given url
            // if image type then display image with full screen mode
            // if video type then display video. Has to request video url first (at different resolution)
        } else if (mediaItem.hasKey()) {
            System.out.println("Video page");
            // call GET http://localhost/api/videos/partial/1/p360 to actually request actual video url at different resolution
            // call GET http://localhost/api/videos/original/1 to request video at original url
        }

        throw new IllegalStateException("Unknown page type with mediaId: " + mediaId);
    }

}
