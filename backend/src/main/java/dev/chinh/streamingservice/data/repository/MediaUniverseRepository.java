package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaUniverse;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaUniverseRepository extends MediaNameEntityRepository<MediaUniverse, Long> {
}
