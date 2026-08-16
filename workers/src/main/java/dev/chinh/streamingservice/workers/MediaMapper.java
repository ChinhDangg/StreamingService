package dev.chinh.streamingservice.workers;

import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
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

    MediaJobDescription mapToJobDescription(MediaDescription source);
}
