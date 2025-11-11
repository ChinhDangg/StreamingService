package dev.chinh.streamingservice;

import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.serve.data.MediaDisplayContent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    MediaSearchItemResponse map(MediaSearchItem source);

    MediaDisplayContent map(MediaDescription source);

    MediaSearchItem map(MediaMetaData source);
}
