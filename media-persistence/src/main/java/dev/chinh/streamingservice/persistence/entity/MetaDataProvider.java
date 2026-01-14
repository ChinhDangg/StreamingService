package dev.chinh.streamingservice.persistence.entity;

import dev.chinh.streamingservice.persistence.projection.MediaNameSearchItem;

import java.util.List;

public interface MetaDataProvider {

    List<MediaNameSearchItem> getTags();

    List<MediaNameSearchItem> getCharacters();

    List<MediaNameSearchItem> getUniverses();

    List<MediaNameSearchItem> getAuthors();

    boolean isGrouper();

    Long getGrouperId();
}
