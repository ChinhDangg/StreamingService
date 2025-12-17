package dev.chinh.streamingservice.backend.event;

import dev.chinh.streamingservice.backend.modify.MediaNameEntityConstant;

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
