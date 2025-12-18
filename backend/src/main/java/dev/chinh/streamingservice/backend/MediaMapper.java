package dev.chinh.streamingservice.backend;

import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.persistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.backend.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.backend.serve.data.MediaDisplayContent;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    MediaSearchItemResponse map(MediaSearchItem source);

    MediaDisplayContent map(MediaDescription source);

    MediaSearchItem map(MediaMetaData source);

    MediaJobDescription mapToJobDescription(MediaDescription source);
}
