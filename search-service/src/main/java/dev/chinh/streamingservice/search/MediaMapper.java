package dev.chinh.streamingservice.search;

import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.search.serve.data.MediaDisplayContent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    default String mapToStringName(MediaNameSearchItem item) {
        if (item == null) return null;
        return item.getName();
    }

    List<String> map(List<MediaNameSearchItem> source);

    MediaSearchItem map(MediaMetaData source);

    MediaSearchItemResponse map(MediaSearchItem source);

    MediaDisplayContent mapDescription(MediaDescription source);
}
