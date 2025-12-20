package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaTag;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaTagRepository extends MediaNameEntityRepository<MediaTag, Long> {
}
