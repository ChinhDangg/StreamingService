package dev.chinh.streamingservice.common.event;


import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;

public interface MediaUpdateEvent {

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
}
