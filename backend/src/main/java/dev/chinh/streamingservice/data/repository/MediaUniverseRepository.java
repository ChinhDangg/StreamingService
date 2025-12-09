package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.entity.MediaUniverse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaUniverseRepository extends MediaNameEntityRepository<MediaUniverse, Long> {

    @Override
    @Query("SELECT new dev.chinh.streamingservice.data.dto.MediaNameEntry(e.name, e.length, e.thumbnail)" +
            " FROM MediaUniverse e")
    Page<MediaNameEntry> findAllNames(Pageable pageable);
}
