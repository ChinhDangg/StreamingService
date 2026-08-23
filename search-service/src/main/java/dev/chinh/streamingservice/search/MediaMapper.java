package dev.chinh.streamingservice.search;

import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import dev.chinh.streamingservice.search.persistence.GrouperMediaMetadata;
import dev.chinh.streamingservice.search.persistence.MediaNameSearchItem;
import dev.chinh.streamingservice.search.persistence.MediaSearchItem;
import dev.chinh.streamingservice.search.serve.data.MediaDisplayContent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    default List<MediaNameSearchItem> mapIdNameMapToNameEntitySearchList(Map<Long, String> items) {
        if (items == null) return null;
        return items.entrySet().stream()
                .map(e -> new MediaNameSearchItem(e.getKey(), e.getValue())).toList();
    }

    default String mapToStringName(MediaNameSearchItem item) {
        if (item == null) return null;
        return item.getName();
    }

    List<String> map(List<MediaNameSearchItem> source);

    @Mapping(target = "groupInfo", ignore = true)
    GrouperMediaMetadata mapToGrouperMediaMetadata(MediaUpdateEvent.MediaCreatedReadyForSearch source);

    @Mapping(target = "groupInfo", ignore = true)
    GrouperMediaMetadata mapToGrouperMediaMetadata(MediaSearchItem source);

    @Mapping(source = "groupInfoId", target = "groupInfo.id")
    @Mapping(source = "groupInfoGrouperId", target = "groupInfo.grouperId")
    @Mapping(source = "groupInfoNumInfo", target = "groupInfo.numInfo")
    MediaSearchItem map(MediaUpdateEvent.MediaCreatedReadyForSearch source);

    MediaSearchItem map(GrouperMediaMetadata source);

    MediaSearchItemResponse map(MediaSearchItem source);

    MediaDisplayContent mapDescription(MediaSearchItem source);
}
