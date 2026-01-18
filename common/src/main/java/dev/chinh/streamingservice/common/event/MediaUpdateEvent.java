package dev.chinh.streamingservice.common.event;


import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;

public interface MediaUpdateEvent {

    // search and backup listener
    record MediaCreated(
            long mediaId,
            MediaType mediaType,
            String bucket,
            String path,
            String absolutePath
    ) implements MediaUpdateEvent{}

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

    // search and object listener
    record MediaThumbnailUpdated(
            long mediaId,
            MediaType mediaType,
            double num,
            String thumbnailObject
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

    record NameEntityCreated(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId
    ) implements MediaUpdateEvent{}

    record NameEntityUpdated(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId
    ) implements MediaUpdateEvent{}

    record NameEntityDeleted(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId
    ) implements MediaUpdateEvent{}


    // for object
    record MediaUpdateEnrichment(
            long mediaId,
            MediaType mediaType,
            String thumbnailObject
    ) implements MediaUpdateEvent {}

    record ThumbnailDeleted(
            String path
    ) implements MediaUpdateEvent {}
}
