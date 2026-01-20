package dev.chinh.streamingservice.common.event;


import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;

public interface MediaUpdateEvent {

    // search, object, and backup listener
    record MediaDeleted(
            long mediaId,
            String bucket,
            String path,
            boolean hasThumbnail,
            String thumbnail,
            MediaType mediaType,
            String absolutePath
    ) implements MediaUpdateEvent{}


    // search and backup listener
    record MediaCreatedReady(
            long mediaId,
            MediaType mediaType,
            String bucket,
            String path,
            String absolutePath,
            String thumbnail
    ) implements MediaUpdateEvent{}

    record MediaThumbnailUpdatedReady(
            long mediaId,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent {}

    record NameEntityCreatedReady(
            long nameEntityId,
            MediaNameEntityConstant nameEntityConstant,
            String thumbnailPath
    ) implements MediaUpdateEvent {}

    record NameEntityThumbnailUpdatedReady(
            long nameEntityId,
            MediaNameEntityConstant nameEntityConstant,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent {}


    // for search only
    record LengthUpdated(
            long mediaId,
            Integer newLength
    ) implements MediaUpdateEvent {}

    record MediaNameEntityUpdated(
            long mediaId,
            MediaNameEntityConstant nameEntityConstant
    ) implements MediaUpdateEvent{}

    record MediaTitleUpdated(
            long mediaId
    ) implements MediaUpdateEvent{}

    record NameEntityUpdated(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId
    ) implements MediaUpdateEvent{}

    record NameEntityDeleted(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String thumbnailPath
    ) implements MediaUpdateEvent{}


    // for object
    record MediaCreated(
            long mediaId,
            MediaType mediaType,
            String thumbnailObject
    ) implements MediaUpdateEvent {}

    record NameEntityCreated(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String thumbnailObject,
            String thumbnailPath
    ) implements MediaUpdateEvent{}

    record MediaThumbnailUpdated(
            long mediaId,
            MediaType mediaType,
            Double num,
            String thumbnailObject
    ) implements MediaUpdateEvent {}
}
