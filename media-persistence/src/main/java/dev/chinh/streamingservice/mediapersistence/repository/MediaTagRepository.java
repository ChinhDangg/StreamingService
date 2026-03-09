package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaTag;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaTagRepository extends MediaNameEntityRepository<MediaTag, Long> {
}
