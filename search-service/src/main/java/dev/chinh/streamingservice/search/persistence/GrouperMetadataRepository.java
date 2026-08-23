package dev.chinh.streamingservice.search.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrouperMetadataRepository extends JpaRepository<GrouperMediaMetadata, Long> {

    GrouperMediaMetadata findByIdAndUserId(long id, long userId);
}
