package dev.chinh.streamingservice;

import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.data.MediaSearchItemResponse;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface MediaMapper {

    MediaSearchItem map(Map<String, Object> source);

    MediaSearchItemResponse map(MediaSearchItem source);
}
