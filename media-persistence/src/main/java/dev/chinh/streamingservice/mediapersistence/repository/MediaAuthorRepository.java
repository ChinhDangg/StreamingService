package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaAuthor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAuthorRepository extends MediaNameEntityRepository<MediaAuthor, Long> {
}
