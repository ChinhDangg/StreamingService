package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaAuthor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAuthorRepository extends MediaNameEntityRepository<MediaAuthor, Long> {
}
