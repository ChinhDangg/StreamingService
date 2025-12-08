package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaGroupMetaData;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaGroupMetaDataRepository extends JpaRepository<MediaGroupMetaData, Long> {

    @Query("""
        SELECT m.mediaMetaDataId
        FROM MediaGroupMetaData m
        WHERE m.grouperMetaDataId = :grouperId
    """)
    Slice<Long> findMediaMetadataIdsByGrouperMetaDataId(@Param("grouperId") Long grouperId, Pageable pageable);

    @Modifying
    @Query("UPDATE MediaGroupMetaData m SET m.numInfo = m.numInfo + 1 WHERE m.mediaMetaDataId = :mediaId")
    void incrementNumInfo(@Param("mediaId") Long mediaId);

    @Query("SELECT m.numInfo FROM MediaGroupMetaData m WHERE m.mediaMetaDataId = :mediaId")
    int getNumInfo(Long mediaId);
}
