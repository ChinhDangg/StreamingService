package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaUniverse;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaUniverseRepository extends MediaNameEntityRepository<MediaUniverse, Long> {

    @Override
    @Query("SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(e.id, e.name, e.length, e.thumbnail)" +
            " FROM MediaUniverse e")
    Page<NameEntityDTO> findAllNames(Pageable pageable);
}
