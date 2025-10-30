package dev.chinh.streamingservice;

import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MediaMapper {

    MediaSearchItemResponse map(MediaSearchItem source);
}
