package dev.chinh.streamingservice.common.event;

import dev.chinh.streamingservice.common.constant.MediaType;

public interface MediaBackupEvent {

    record MediaCreated(
            String bucket,
            String path,
            MediaType mediaType
    ) implements MediaBackupEvent {}
}
