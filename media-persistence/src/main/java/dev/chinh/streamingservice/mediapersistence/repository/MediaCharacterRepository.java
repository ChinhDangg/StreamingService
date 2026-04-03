package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaCharacter;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaCharacterRepository extends MediaNameEntityRepository<MediaCharacter, Long> {

    @Override
    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(e.id, e.name, e.length, e.thumbnail)
        FROM MediaCharacter e
        WHERE e.userId = :userId
    """)
    Page<NameEntityDTO> findAllNames(@Param("userId") long userId, Pageable pageable);
}
