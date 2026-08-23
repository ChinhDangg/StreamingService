package dev.chinh.streamingservice.mediahandler;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    default Map<Long, String> mapNameEntitySearchListToIdNameMap(List<MediaNameSearchItem> items) {
        if (items == null) return null;
        return items.stream().collect(Collectors.toMap(
                MediaNameSearchItem::getId,
                MediaNameSearchItem::getName
        ));
    }

    @Mapping(source = "groupInfo.id", target = "groupInfoId")
//    @Mapping(target = "groupInfoGrouperId", expression = "java(source.getGrouperId())")
    @Mapping(source = "grouperId", target = "groupInfoGrouperId")
    @Mapping(source = "groupInfo.numInfo", target = "groupInfoNumInfo")
    MediaUpdateEvent.MediaCreatedReadyForSearch map(MediaMetaData source);
}
