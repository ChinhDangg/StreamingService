package dev.chinh.streamingservice.mediapersistence.entity;

import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;

import java.util.List;

public interface MetaDataProvider {

    List<MediaNameSearchItem> getTags();

    List<MediaNameSearchItem> getCharacters();

    List<MediaNameSearchItem> getUniverses();

    List<MediaNameSearchItem> getAuthors();

    boolean isGrouper();

    Long getGrouperId();
}
