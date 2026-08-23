package dev.chinh.streamingservice.search.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaGroupInfoRepository extends JpaRepository<MediaGroupInfo, Long> {

    @Query("""
        SELECT g.grouperMediaMetadata.id
        FROM MediaGroupInfo g
        WHERE g.grouperInfo.id = :grouperId
    """)
    Slice<Long> findMediaMetadataIdsByGrouperId(@Param("grouperId") Long grouperId, Pageable pageable);

}
