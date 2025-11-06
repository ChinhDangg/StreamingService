package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaTag;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaTagRepository extends MediaNameEntityRepository<MediaTag, Long> {
}
