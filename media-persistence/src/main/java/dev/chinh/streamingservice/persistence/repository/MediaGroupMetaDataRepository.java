package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaGroupMetaData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query("UPDATE MediaGroupMetaData m SET m.numInfo = m.numInfo + 1 WHERE m.mediaMetaDataId = :mediaId")
    void incrementNumInfo(@Param("mediaId") long mediaId);

    @Query(value = """
        UPDATE media.media_groups
        SET num_info = num_info + 1
        WHERE id = :id
        RETURNING num_info
    """, nativeQuery = true
    )
    Integer incrementNumInfoReturning(@Param("id") long id);

    @Modifying
    @Transactional
    @Query("""
        UPDATE MediaGroupMetaData m
        SET m.numInfo = m.numInfo - 1
        WHERE m.mediaMetaDataId = :mediaId
        AND m.numInfo > 0
   """)
    void decrementNumInfo(@Param("mediaId") long mediaId);

    @Query("SELECT m.numInfo FROM MediaGroupMetaData m WHERE m.mediaMetaDataId = :mediaId")
    int getNumInfo(long mediaId);
}
