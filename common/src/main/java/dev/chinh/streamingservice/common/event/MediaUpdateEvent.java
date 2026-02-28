package dev.chinh.streamingservice.common.event;


import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;

import java.time.Instant;
import java.util.List;

public interface MediaUpdateEvent {

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
            long nameEntityId,
            String thumbnailPath
    ) implements MediaUpdateEvent{}

    record NameEntityDeleted(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String thumbnailPath
    ) implements MediaUpdateEvent{}

    record NameEntityUpdated(
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent{}


    record MediaEnriched(
            String fileId,
            long mediaId,
            MediaType mediaType,
            String thumbnailObject,
            boolean searchable,
            long size,
            int length
    ) implements MediaUpdateEvent {}

    record MediaThumbnailUpdated(
            long mediaId,
            MediaType mediaType,
            Double num,
            String bucket,
            String thumbnailObject
    ) implements MediaUpdateEvent {}

    record MediaThumbnailUpdateInitiated(
            long mediaId,
            MediaType mediaType,
            int num
    ) implements MediaUpdateEvent {}

    record ObjectDeleted(
            String bucket,
            List<String> objectNames
    ) implements MediaUpdateEvent {}


    record FileCreated(
            String bucket,
            String objectName,
            String fileName,
            long size,
            Long mediaId,
            MediaType mediaType,
            String thumbnailObject
    ) implements MediaUpdateEvent {}

    record FileDeleted(
            String fileId,
            String fileName,
            boolean isNotDirectory,
            Long mediaId
    ) implements MediaUpdateEvent {}

    record FileToMediaInitiated(
            String fileId,
            MediaType mediaType,
            String bucket,
            String objectName,
            String fileName,
            Instant uploadDate,
            Long parentMediaId,
            Integer childNum,
            Long childMediaId,
            boolean searchable
    ) implements MediaUpdateEvent {}

    record DirectoryToMediaInitiated(
            String fileId,
            long mediaId,
            MediaType mediaType,
            boolean searchable,
            String thumbnailObject,
            long initialSize,
            int offset
    ) implements MediaUpdateEvent {}

    record NestedDirectoryToMediaInitiated(
            String fileId,
            long mediaId,
            MediaType parentType,
            MediaType childType,
            boolean childSearchable,
            String thumbnailObject,
            int offset
    ) implements MediaUpdateEvent {}

    record MediaCreatedReady(
            String fileId,
            long mediaId,
            MediaType mediaType,
            String thumbnail,
            int length
    ) implements MediaUpdateEvent{}

    record MediaThumbnailUpdatedReady(
            long mediaId,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent {}
}
