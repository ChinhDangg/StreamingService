package dev.chinh.streamingservice.common.event;


import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface MediaUpdateEvent {

    // for search only
    record LengthUpdated(
            List<Long> mediaIds,
            int deltaLength,
            long version
    ) implements MediaUpdateEvent {
        public LengthUpdated(List<Long> mediaIds, Integer deltaLength) {
            this(mediaIds, deltaLength, Instant.now().toEpochMilli());
        }
    }

    record MediaNameEntityUpdated(
            String userId,
            long mediaId,
            MediaNameEntityConstant nameEntityConstant,
            Map<Long, String> nameEntityIdsToNames
    ) implements MediaUpdateEvent{}

    record MediaTitleUpdated(
            String userId,
            long mediaId,
            String title
    ) implements MediaUpdateEvent{}


    record NameEntityCreated(
            String userId,
            MediaNameEntityConstant nameEntityConstant,
            long nameEntityId,
            String name,
            int length,
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
            String newName,
            String oldThumbnail,
            String newThumbnail
    ) implements MediaUpdateEvent{}

    record NameEntityLengthUpdated(
            String userId,
            MediaNameEntityConstant nameEntityConstant,
            Long[] nameEntityIds,
            int deltaLength
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
            boolean isLast,
            boolean addAsVideo,
            String nameUpdateListAsJson
    ) implements MediaUpdateEvent {}

    record FileDeleted(
            String userId,
            String fileId,
            String fileName,
            boolean isNotDirectory,
            MediaType mediaType,
            Long mediaId
    ) implements MediaUpdateEvent {}

    record NestedDirectoryToGrouperMediaInitiated(
            String userId,
            String fileId,
            Long mediaId,
            String bucket,
            String objectName,
            String fileName,
            Instant uploadDate,
            boolean childSearchable,
            MediaType childMediaType,
            int length
    ) implements MediaUpdateEvent {}

    record GrouperMediaCreatedReady(
            String userId,
            String fileid,
            long mediaId,
            int length,
            String bucket,
            String objectName
    ) implements MediaUpdateEvent{}



    record DirectoryToAlbumMediaInitiated(
            String userId,
            String fileId,
            String bucket,
            String objectName,
            String fileName,
            Instant uploadDate,
            boolean searchable,
            long size,
            int length,
            Long parentMediaId
    ) implements MediaUpdateEvent {}

    record FileToVideoMediaInitiated(
            String userId,
            String fileId,
            String bucket,
            String objectName,
            String fileName,
            Instant uploadDate,
            String nameUpdateListAsJson
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

    record MediaCreatedReadyForSearch(
            @JsonProperty(ContentMetaData.ID)
            long id,
            @JsonProperty(ContentMetaData.USER_ID)
            long userId,
            @JsonProperty(ContentMetaData.TITLE)
            String title,
            @JsonProperty(ContentMetaData.BUCKET)
            String bucket,
            @JsonProperty(ContentMetaData.KEY)
            String key,
            @JsonProperty(ContentMetaData.THUMBNAIL)
            String thumbnail,
            @JsonProperty(ContentMetaData.PREVIEW)
            String preview,
            @JsonProperty(ContentMetaData.LENGTH)
            int length,
            @JsonProperty(ContentMetaData.SIZE)
            long size,
            @JsonProperty(ContentMetaData.WIDTH)
            int width,
            @JsonProperty(ContentMetaData.HEIGHT)
            int height,
            @JsonProperty(ContentMetaData.UPLOAD_DATE)
            Instant uploadDate,
            @JsonProperty(ContentMetaData.YEAR)
            short year,
            @JsonProperty(ContentMetaData.MEDIA_TYPE)
            MediaType mediaType,
            @JsonProperty(ContentMetaData.TAGS)
            Map<Long, String> tags,
            @JsonProperty(ContentMetaData.CHARACTERS)
            Map<Long, String> characters,
            @JsonProperty(ContentMetaData.UNIVERSES)
            Map<Long, String> universes,
            @JsonProperty(ContentMetaData.AUTHORS)
            Map<Long, String> authors,
            Long groupInfoId,
            @JsonProperty(ContentMetaData.GROUPER_ID)
            Long groupInfoGrouperId,
            @JsonProperty(ContentMetaData.NUM_INFO)
            String groupInfoNumInfo
    ) implements MediaUpdateEvent {}

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
            String newParentId,
            String oldParentId,
            String oldIdPath,
            String oldPath,
            String newPath,
            String fileType
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
            String fileName,
            boolean oldParentIsGrouper,
            Long newGroupInfoId
    ) implements MediaUpdateEvent {}

    record FileRenamed(
            String fileId,
            String filePath,
            String newFileName
    ) implements MediaUpdateEvent {}

    record MediaFileLengthUpdate(
            String userId,
            List<Long> mediaIds,
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
