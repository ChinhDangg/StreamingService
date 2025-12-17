package dev.chinh.streamingservice.backend.data.entity;

import java.util.List;

public interface MetaDataProvider {

    List<String> getTags();

    List<String> getCharacters();

    List<String> getUniverses();

    List<String> getAuthors();

    boolean isGrouper();

    Long getGrouperId();
}
