package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.entity.MediaCharacter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaCharacterRepository extends MediaNameEntityRepository<MediaCharacter, Long> {

    @Override
    @Query("SELECT e.name, e.length, e.uploadDate, e.thumbnail FROM MediaCharacter e")
    Page<MediaNameEntry> findAllNames(Pageable pageable);
}
