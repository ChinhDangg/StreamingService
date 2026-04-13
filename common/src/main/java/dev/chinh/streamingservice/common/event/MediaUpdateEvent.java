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
            String userId,
            long mediaId,
            MediaNameEntityConstant nameEntityConstant
    ) implements MediaUpdateEvent{}

    record MediaTitleUpdated(
            String userId,
            long mediaId
    ) implements MediaUpdateEvent{}


    record NameEntityCreated(
            String userId,
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
            String userId,
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent{}

    record MediaPreviewUpdated(
            String userId,
            long mediaId,
            String previewObject
    ) implements MediaUpdateEvent{}


    record MediaEnriched(
            String userId,
            String fileId,
            long mediaId,
            MediaType mediaType,
            String thumbnailObject,
            boolean searchable,
            long size,
            int length
    ) implements MediaUpdateEvent {}

    record MediaThumbnailUpdated(
            String userId,
            long mediaId,
            MediaType mediaType,
            Double num,
            String bucket,
            String thumbnailObject
    ) implements MediaUpdateEvent {}

    record MediaThumbnailUpdateInitiated(
            String userId,
            long mediaId,
            MediaType mediaType,
            int num
    ) implements MediaUpdateEvent {}

    record ObjectDeleted(
            String bucket,
            List<String> objectNames
    ) implements MediaUpdateEvent {}

    record ThumbnailDeleted(
            String objectName
    ) implements MediaUpdateEvent {}


    record FileCreated(
            String userId,
            String bucket,
            String objectName,
            String fileName,
            long size,
            Long mediaId,
            MediaType mediaType,
            String thumbnailObject,
            boolean isLast
    ) implements MediaUpdateEvent {}

    record FileDeleted(
            String userId,
            String fileId,
            String fileName,
            boolean isNotDirectory,
            Long mediaId
    ) implements MediaUpdateEvent {}

    record FileToMediaInitiated(
            String userId,
            String fileId,
            MediaType mediaType,
            String bucket,
            String objectName,
            String fileName,
            Instant uploadDate,
            Long parentMediaId,
            Long childMediaId,
            boolean searchable,
            boolean updateParentLength
    ) implements MediaUpdateEvent {}

    record DirectoryToMediaInitiated(
            String userId,
            String fileId,
            long mediaId,
            MediaType mediaType,
            boolean searchable,
            boolean updateParentLength,
            String thumbnailObject,
            long initialSize,
            int offset
    ) implements MediaUpdateEvent {}

    record NestedDirectoryToMediaInitiated(
            String userId,
            String fileId,
            long mediaId,
            MediaType parentType,
            MediaType childType,
            boolean childSearchable,
            String thumbnailObject,
            int offset
    ) implements MediaUpdateEvent {}

    record MediaCreatedReady(
            String userId,
            String fileId,
            long mediaId,
            MediaType mediaType,
            String thumbnail,
            int length,
            Integer width,
            Integer height
    ) implements MediaUpdateEvent{}

    record MediaThumbnailUpdatedReady(
            long mediaId,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent {}

    record DirectoryCreated(
            String fileId,
            String dirPath
    ) implements MediaUpdateEvent {}

    record DirectoryMoved(
            String userId,
            String fileId,
            String parentId,
            String oldIdPath,
            String oldPath,
            String newPath
    ) implements MediaUpdateEvent {}

    record FileMoved(
            String fileId,
            String oldPath,
            String newPath
    ) implements MediaUpdateEvent {}

    record GrouperItemMoved(
            String userId,
            long childMediaId,
            Long parentMediaId,
            String fileName
    ) implements MediaUpdateEvent {}

    record FileRenamed(
            String fileId,
            String filePath,
            String newFileName
    ) implements MediaUpdateEvent {}

    record MediaFileLengthUpdate(
            String userId,
            long mediaId,
            int length
    ) implements MediaUpdateEvent {}



    record ControlAddAsVideo(
            String userId,
            String fileId
    ) implements MediaUpdateEvent {}

    record ControlAddAsAlbum(
            String userId,
            String fileId
    ) implements MediaUpdateEvent {}

    record ControlAddAsGrouper(
            String userId,
            String fileId
    ) implements MediaUpdateEvent {}

    record ControlAddAuthor(
            String userId,
            String author
    ) implements MediaUpdateEvent {}

    record ControlAddTag(
            String userId,
            String tag
    ) implements MediaUpdateEvent {}

    record ControlAddNameEntitiesToMedia(
            String userId,
            long mediaId,
            Long[] nameEntityIds,
            MediaNameEntityConstant nameEntityConstant
    )  implements MediaUpdateEvent {}
}
