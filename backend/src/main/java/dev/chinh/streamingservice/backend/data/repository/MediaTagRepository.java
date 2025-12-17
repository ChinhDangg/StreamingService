package dev.chinh.streamingservice.backend.data.repository;

import dev.chinh.streamingservice.backend.data.entity.MediaTag;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaTagRepository extends MediaNameEntityRepository<MediaTag, Long> {
}
