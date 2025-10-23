package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaGroupMetaData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaGroupMetaDataRepository extends JpaRepository<MediaGroupMetaData, Long> {

    long countByGrouperMetaDataId(long grouperMetaDataId);
}
