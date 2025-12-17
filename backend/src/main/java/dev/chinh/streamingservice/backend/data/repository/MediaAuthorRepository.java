package dev.chinh.streamingservice.backend.data.repository;

import dev.chinh.streamingservice.backend.data.entity.MediaAuthor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAuthorRepository extends MediaNameEntityRepository<MediaAuthor, Long> {
}
