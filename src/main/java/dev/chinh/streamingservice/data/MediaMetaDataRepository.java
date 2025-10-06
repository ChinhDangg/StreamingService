package dev.chinh.streamingservice.data;

import dev.chinh.streamingservice.data.entity.MediaMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaMetaDataRepository extends JpaRepository<MediaMetaData, Long> {

    @Query("""
        SELECT DISTINCT m FROM MediaMetaData m
        LEFT JOIN FETCH m.tags
        LEFT JOIN FETCH m.characters
        LEFT JOIN FETCH m.universes
        LEFT JOIN FETCH m.authors
        WHERE m.id = :id
    """)
    Optional<MediaMetaData> findByIdWithAllInfo(@Param("id") Long id);
}
