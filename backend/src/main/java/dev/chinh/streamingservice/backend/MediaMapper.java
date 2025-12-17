package dev.chinh.streamingservice.backend;

import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.backend.data.projection.MediaNameEntry;
import dev.chinh.streamingservice.backend.data.entity.MediaDescription;
import dev.chinh.streamingservice.backend.data.entity.MediaMetaData;
import dev.chinh.streamingservice.backend.data.entity.MediaNameEntity;
import dev.chinh.streamingservice.backend.search.data.MediaSearchItem;
import dev.chinh.streamingservice.backend.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.backend.serve.data.MediaDisplayContent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    MediaSearchItemResponse map(MediaSearchItem source);

    MediaDisplayContent map(MediaDescription source);

    MediaSearchItem map(MediaMetaData source);

    MediaNameEntry map(MediaNameEntity source);

    MediaJobDescription mapToJobDescription(MediaDescription source);
}
