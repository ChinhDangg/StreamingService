package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaCharacter;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaCharacterRepository extends MediaNameEntityRepository<MediaCharacter, Long> {
}
