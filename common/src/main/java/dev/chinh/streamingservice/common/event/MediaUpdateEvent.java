package dev.chinh.streamingservice.common.event;


import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;

public interface MediaUpdateEvent {

    // for search
    record LengthUpdated(
            long mediaId,
            Integer newLength
    ) implements MediaUpdateEvent {}

    record MediaCreated(
            long mediaId,
            boolean isGrouper
    ) implements MediaUpdateEvent{}

    record MediaNameEntityUpdated(
            long mediaId,
            MediaNameEntityConstant nameEntityConstant
    ) implements MediaUpdateEvent{}

    record MediaTitleUpdated(
            long mediaId
    ) implements MediaUpdateEvent{}

    record MediaDeleted(
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
    record MediaObjectDeleted(
            String bucket,
            String path,
            boolean hasThumbnail,
            String thumbnail,
            MediaType mediaType
    ) implements MediaUpdateEvent {}

    record ThumbnailObjectDeleted(
            String path
    ) implements MediaUpdateEvent {}

    record MediaUpdateEnrichment(
            long mediaId,
            MediaType mediaType,
            String mediaSearchTopic,
            MediaCreated mediaCreatedEvent
    ) implements MediaUpdateEvent {}

    // for backup
    record MediaBackupCreated(
            String bucket,
            String path,
            String absolutePath,
            MediaType mediaType
    ) implements MediaUpdateEvent {}

    record MediaBackupDeleted(
            String absolutePath,
            MediaType mediaType
    ) implements MediaUpdateEvent {}
}
