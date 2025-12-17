package dev.chinh.streamingservice.backend.data.repository;

import dev.chinh.streamingservice.backend.data.entity.MediaGroupMetaData;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MediaGroupMetaDataRepository extends JpaRepository<MediaGroupMetaData, Long> {

    @Query("""
        SELECT m.mediaMetaDataId
        FROM MediaGroupMetaData m
        WHERE m.grouperMetaDataId = :grouperId
    """)
    Slice<Long> findMediaMetadataIdsByGrouperMetaDataId(@Param("grouperId") Long grouperId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE MediaGroupMetaData m SET m.numInfo = m.numInfo + 1 WHERE m.mediaMetaDataId = :mediaId")
    void incrementNumInfo(@Param("mediaId") long mediaId);

    @Query(
            value = "UPDATE media_groups " +
                    "SET num_info = num_info + 1 " +
                    "WHERE id = :id " +
                    "RETURNING num_info",
            nativeQuery = true
    )
    Integer incrementNumInfoReturning(@Param("id") long id);

    @Query("SELECT m.numInfo FROM MediaGroupMetaData m WHERE m.mediaMetaDataId = :mediaId")
    int getNumInfo(long mediaId);
}
