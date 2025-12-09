package dev.chinh.streamingservice.event;

import dev.chinh.streamingservice.modify.MediaNameEntityConstant;

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
}
