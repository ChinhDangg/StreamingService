package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaAuthor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAuthorRepository extends MediaNameEntityRepository<MediaAuthor, Long> {
}
