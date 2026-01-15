package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaCharacter;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaCharacterRepository extends MediaNameEntityRepository<MediaCharacter, Long> {

    @Override
    @Query("SELECT new dev.chinh.streamingservice.persistence.projection.NameEntityDTO(e.id, e.name, e.length, e.thumbnail)" +
            " FROM MediaCharacter e")
    Page<NameEntityDTO> findAllNames(Pageable pageable);
}
