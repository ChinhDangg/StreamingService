package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaGroupMetaData;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaGroupMetaDataRepository extends JpaRepository<MediaGroupMetaData, Long> {

    @Query("""
        SELECT m.mediaMetaDataId
        FROM MediaGroupMetaData m
        WHERE m.grouperMetaDataId = :grouperId
        ORDER BY m.numInfo
    """)
    Slice<Long> findMediaMetadataIdsByGrouperMetaDataId(@Param("grouperId") Long grouperId, Pageable pageable);
}
