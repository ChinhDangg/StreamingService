package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.probe.ImageMetadata;
import dev.chinh.streamingservice.mediaobject.probe.MediaProbe;
import dev.chinh.streamingservice.mediaobject.probe.VideoMetadata;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaObjectService {

    private final MinIOService minIOService;
    private final MediaProbe mediaProbe;
    private final ThumbnailService thumbnailService;

    private final MediaMetaDataRepository mediaMetaDataRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void handleMediaEnrich(MediaUpdateEvent.MediaEnriched event) throws Exception {
        MediaMetaData mediaMetaData = getMediaMetadataById(Long.parseLong(event.userId()), event.mediaId());
        if (event.mediaType() == MediaType.VIDEO) {
            VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), VideoMetadata.class);
            mediaMetaData.setFrameRate(videoMetadata.frameRate());
            mediaMetaData.setFormat(videoMetadata.format());
            mediaMetaData.setSize(videoMetadata.size());
            mediaMetaData.setWidth(videoMetadata.width());
            mediaMetaData.setHeight(videoMetadata.height());
            mediaMetaData.setLength((int) videoMetadata.durationSeconds());
            mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject(), mediaMetaData.getLength(), null));
        } else {
            mediaMetaData.setSize(event.size());
            mediaMetaData.setLength(event.length());
            if (event.searchable()) {
                MediaType thumbnailMediaType = MediaType.detectMediaType(mediaMetaData.getKey());
                if (thumbnailMediaType == MediaType.IMAGE) {
                    ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), ImageMetadata.class);
                    mediaMetaData.setWidth(imageMetadata.width());
                    mediaMetaData.setHeight(imageMetadata.height());
                    mediaMetaData.setFormat(imageMetadata.format());
                    mediaMetaData.setFrameRate((short) 1);
                    mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject()));
                } else if (thumbnailMediaType == MediaType.VIDEO) {
                    VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), VideoMetadata.class);
                    mediaMetaData.setWidth(videoMetadata.width());
                    mediaMetaData.setHeight(videoMetadata.height());
                    mediaMetaData.setFormat(videoMetadata.format());
                    mediaMetaData.setFrameRate(videoMetadata.frameRate());
                    mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject(), (int) videoMetadata.durationSeconds(), null));
                }
            }
        }
        mediaMetaDataRepository.save(mediaMetaData);

        String topic = event.searchable()
                ? EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC
                : EventTopics.MEDIA_FILE_TOPIC; // not searchable - no thumbnail - no backup to save the thumbnail
        eventPublisher.publishEvent(new MediaObjectEventProducer.EventWrapper(
                topic,
                new MediaUpdateEvent.MediaCreatedReady(
                        event.userId(),
                        event.fileId(),
                        event.mediaId(),
                        event.mediaType(),
                        mediaMetaData.getThumbnail(),
                        mediaMetaData.getLength(),
                        mediaMetaData.getWidth(),
                        mediaMetaData.getHeight()
                )
        ));
    }

    public void handleDeleteObject(MediaUpdateEvent.ObjectDeleted event) {
        List<DeleteObject> objects = event.objectNames().stream().map(DeleteObject::new).toList();
        if (event.bucket() == null) {
            System.err.println("Bucket is null, skipping delete objects");
            return;
        }
        minIOService.removeBulk(event.bucket(), objects);
    }

    @Transactional
    public void handleUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        MediaMetaData mediaMetaData = getMediaMetadataById(Long.parseLong(event.userId()), event.mediaId());

        boolean sameName = event.thumbnailObject().equals(mediaMetaData.getThumbnail());

        if (event.num() != null && event.mediaType() == MediaType.VIDEO) {
            thumbnailService.generateThumbnailFromVideo(
                    mediaMetaData.getBucket(),
                    mediaMetaData.getKey(),
                    event.thumbnailObject(),
                    mediaMetaData.getLength(),
                    event.num()
            );
        } else if (event.num() != null && event.mediaType() == MediaType.ALBUM) {
            MediaType thumbnailType = MediaType.detectMediaType(event.thumbnailObject());
            if (thumbnailType == MediaType.IMAGE) {
                ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), event.thumbnailObject()), ImageMetadata.class);
                mediaMetaData.setWidth(imageMetadata.width());
                mediaMetaData.setHeight(imageMetadata.height());
                mediaMetaData.setFormat(imageMetadata.format());
            } else if (thumbnailType == MediaType.VIDEO) {
                VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), event.thumbnailObject()), VideoMetadata.class);
                mediaMetaData.setWidth(videoMetadata.width());
                mediaMetaData.setHeight(videoMetadata.height());
            }
        } else if (!sameName) {
            minIOService.removeFile(event.bucket(), mediaMetaData.getThumbnail());

            mediaMetaDataRepository.updateMediaThumbnail(Long.parseLong(event.userId()), mediaMetaData.getId(), event.thumbnailObject());
        }

        String topic = sameName ? EventTopics.MEDIA_BACKUP_TOPIC : EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC;
        eventPublisher.publishEvent(new MediaObjectEventProducer.EventWrapper(
                topic,
                new MediaUpdateEvent.MediaThumbnailUpdatedReady(event.mediaId(), mediaMetaData.getThumbnail(), event.thumbnailObject()))
        );
    }

    public void handleDeleteThumbnail(MediaUpdateEvent.ThumbnailDeleted event) throws Exception {
        minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.objectName());
    }


    private MediaMetaData getMediaMetadataById(long userId, long id) {
        return mediaMetaDataRepository.findByUserIdAndId(userId, id).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + id)
        );
    }
}
