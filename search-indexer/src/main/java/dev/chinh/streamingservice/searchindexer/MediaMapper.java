package dev.chinh.streamingservice.searchindexer;

import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    MediaSearchItem map(MediaMetaData source);
}
