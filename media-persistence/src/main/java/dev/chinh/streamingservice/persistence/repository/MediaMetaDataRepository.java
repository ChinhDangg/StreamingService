package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaMetaDataRepository extends JpaRepository<MediaMetaData, Long> {

    @Query("""
        SELECT DISTINCT m FROM MediaMetaData m
        LEFT JOIN FETCH m.tags
        LEFT JOIN FETCH m.characters
        LEFT JOIN FETCH m.universes
        LEFT JOIN FETCH m.authors
        LEFT JOIN FETCH m.groupInfo
        WHERE m.id = :id
    """)
    Optional<MediaMetaData> findByIdWithAllInfo(@Param("id") Long id);


    @Query("""
        SELECT DISTINCT m FROM MediaMetaData m
        LEFT JOIN FETCH m.tags
        LEFT JOIN FETCH m.characters
        LEFT JOIN FETCH m.universes
        LEFT JOIN FETCH m.authors
        LEFT JOIN FETCH m.groupInfo
    """)
    List<MediaMetaData> findAllWithAllInfo(Pageable page);


    @Query("SELECT m.length FROM MediaMetaData m WHERE m.id = :id")
    int getMediaLength(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE MediaMetaData m SET m.length = m.length + 1 WHERE m.id = :id")
    void incrementLength(@Param("id") Long id);

    // returning query doesn't work with modifying - remove annotation or create a custom RepositoryImpl
    // and then use an entity manager to create the query.
    // Removing the modifying annotation works for spring >= 3.2
    //@Modifying
    //@Transactional
    @Query(value = """
        UPDATE media.media_metadata
        SET length = length + 1
        WHERE id = :id
        RETURNING length
    """, nativeQuery = true
    )
    Integer incrementLengthReturning(@Param("id") Long id);

//    @Modifying
//    @Transactional
    @Query(value = """
        UPDATE media.media_metadata
        SET length = length - 1
        WHERE id = :id
        AND length > 0
        RETURNING length
    """, nativeQuery = true)
    Integer decrementLengthReturning(@Param("id") Long id);


    @Query("SELECT m.title FROM MediaMetaData m WHERE m.id = :id")
    String getMediaTitle(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE MediaMetaData m SET m.title = :title WHERE m.id = :id")
    int updateMediaTitle(Long id, String title);


    @Query("""
        SELECT new dev.chinh.streamingservice.persistence.projection.NameEntityDTO(a.id, a.name)
        FROM MediaMetaData m
        JOIN m.authors a
        WHERE m.id = :mediaId
    """)
    List<NameEntityDTO> findAuthorsByMediaId(long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.persistence.projection.NameEntityDTO(c.id, c.name)
        FROM MediaMetaData m
        JOIN m.characters c
        WHERE m.id = :mediaId
    """)
    List<NameEntityDTO> findCharactersByMediaId(long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.persistence.projection.NameEntityDTO(u.id, u.name)
        FROM MediaMetaData m
        JOIN m.universes u
        WHERE m.id = :mediaId
    """)
    List<NameEntityDTO> findUniversesByMediaId(long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.persistence.projection.NameEntityDTO(t.id, t.name)
        FROM MediaMetaData m
        JOIN m.tags t
        WHERE m.id = :mediaId
    """)
    List<NameEntityDTO> findTagsByMediaId(long mediaId);


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.authors_media (media_id, authors_id)
        VALUES (:mediaId, :authorId)
    """, nativeQuery = true)
    int addAuthorToMedia(Long mediaId, Long authorId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.characters_media (media_id, characters_id)
        VALUES (:mediaId, :characterId)
    """, nativeQuery = true)
    int addCharacterToMedia(Long mediaId, Long characterId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.universes_media (media_id, universes_id)
        VALUES (:mediaId, :universeId)
    """, nativeQuery = true)
    int addUniverseToMedia(Long mediaId, Long universeId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.tags_media (media_id, tags_id)
        VALUES (:mediaId, :tagId)
    """, nativeQuery = true)
    int addTagToMedia(Long mediaId, Long tagId);


    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.authors_media am
        WHERE am.media_id = :mediaId
        AND am.authors_id = :authorId
    """, nativeQuery = true)
    int deleteAuthorFromMedia(Long mediaId, Long authorId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.characters_media cm
        WHERE cm.media_id = :mediaId
        AND cm.characters_id = :characterId
    """, nativeQuery = true)
    int deleteCharacterFromMedia(Long mediaId, Long characterId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.universes_media um
        WHERE um.media_id = :mediaId
        AND um.universes_id = :universeId
    """, nativeQuery = true)
    int deleteUniverseFromMedia(Long mediaId, Long universeId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.tags_media tm
        WHERE tm.media_id = :mediaId
        AND tm.tags_id = :tagId
    """, nativeQuery = true)
    int deleteTagFromMedia(Long mediaId, Long tagId);


    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.authors
        SET length = length - 1
        WHERE id IN (
            SELECT authors_id
            FROM media.authors_media
            WHERE media_id = :mediaId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementAuthorLengths(@Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.characters
        SET length = length - 1
        WHERE id IN (
            SELECT characters_id
            FROM media.characters_media
            WHERE media_id = :mediaId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementCharacterLengths(@Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.universes
        SET length = length - 1
        WHERE id IN (
            SELECT universes_id
            FROM media.universes_media
            WHERE media_id = :mediaId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementUniverseLengths(@Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.tags
        SET length = length - 1
        WHERE id IN (
            SELECT tags_id
            FROM media.tags_media
            WHERE media_id = :mediaId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementTagLengths(@Param("mediaId") long mediaId);
}
