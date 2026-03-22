package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaGroupMetaData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaGroupMetaDataRepository extends JpaRepository<MediaGroupMetaData, Long> {

    @Query("""
        SELECT m.mediaMetaDataId
        FROM MediaGroupMetaData m
        WHERE m.grouperMetaDataId = :grouperId
    """)
    Slice<Long> findMediaMetadataIdsByGrouperMetaDataId(@Param("grouperId") Long grouperId, Pageable pageable);

    // Spring generates: SELECT * FROM media_group_meta_data WHERE grouper_id = ? LIMIT 1
    Optional<MediaGroupMetaData> findFirstByGrouperMetaDataId(Long grouperId);
}
