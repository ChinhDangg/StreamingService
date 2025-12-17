package dev.chinh.streamingservice.backend.data.repository;

import dev.chinh.streamingservice.backend.data.projection.MediaNameEntry;
import dev.chinh.streamingservice.backend.data.entity.MediaCharacter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaCharacterRepository extends MediaNameEntityRepository<MediaCharacter, Long> {

    @Override
    @Query("SELECT new dev.chinh.streamingservice.backend.data.projection.MediaNameEntry(e.name, e.length, e.thumbnail)" +
            " FROM MediaCharacter e")
    Page<MediaNameEntry> findAllNames(Pageable pageable);
}
