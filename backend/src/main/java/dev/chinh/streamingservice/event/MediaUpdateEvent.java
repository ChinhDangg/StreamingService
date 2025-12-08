package dev.chinh.streamingservice.event;

import dev.chinh.streamingservice.modify.MediaNameEntityConstant;

public class MediaUpdateEvent {

    public record LengthUpdated(
            long mediaId,
            Integer newLength
    ) {}

    public record MediaCreated(
            long mediaId,
            boolean isGrouper
    ) {}

    public record MediaNameEntityUpdated(
            long mediaId,
            MediaNameEntityConstant nameEntityConstant
    ) {}

    public record MediaTitleUpdated(
            long mediaId
    ) {}


    public record NameEntityCreated(
            long nameEntityId
    ) {}
}
