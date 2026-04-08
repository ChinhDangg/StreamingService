package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
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
            AND m.userId = :userId
    """)
    Optional<MediaMetaData> findByIdWithAllInfo(@Param("userId") long userId, @Param("id") long id);

    Optional<MediaMetaData> findByUserIdAndId(long userId, long id);

    // returning query doesn't work with modifying - remove annotation or create a custom RepositoryImpl
    // and then use an entity manager to create the query.
    // Removing the modifying annotation works for spring >= 3.2
    //@Modifying
    //@Transactional
    @Query(value = """
        UPDATE media.media_metadata
        SET length = length + 1
        WHERE id = :id
            AND media_metadata.user_id = :userId
        RETURNING length
    """, nativeQuery = true
    )
    Integer incrementLengthReturning(@Param("userId") long userId, @Param("id") long id);

//    @Modifying
//    @Transactional
    @Query(value = """
        UPDATE media.media_metadata
        SET length = length - 1
        WHERE id = :id
            AND length > 0
            AND media_metadata.user_id = :userId
        RETURNING length
    """, nativeQuery = true)
    Integer decrementLengthReturning(@Param("userId") long userId, @Param("id") long id);


    @Query("""
        SELECT m.title
        FROM MediaMetaData m
        WHERE m.id = :id
            AND m.userId = :userId
    """)
    String getMediaTitle(@Param("userId") long userId, @Param("id") long id);

    @Modifying
    @Transactional
    @Query("""
        UPDATE MediaMetaData m
        SET m.title = :title
        WHERE m.id = :id
            AND m.userId = :userId
    """)
    int updateMediaTitle(@Param("userId") long userId, @Param("id") long id, @Param("title") String title);

    @Modifying
    @Transactional
    @Query("""
        UPDATE MediaMetaData m
        SET m.thumbnail = :thumbnail
        WHERE m.id = :id
            AND m.userId = :userId
    """)
    int updateMediaThumbnail(@Param("userId") long userId, @Param("id") long id, @Param("thumbnail") String thumbnail);

    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(a.id, a.name)
        FROM MediaMetaData m
        JOIN m.authors a
        WHERE m.id = :mediaId
            AND m.userId = :userId
    """)
    List<NameEntityDTO> findAuthorsByMediaId(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(c.id, c.name)
        FROM MediaMetaData m
        JOIN m.characters c
        WHERE m.id = :mediaId
            AND m.userId = :userId
    """)
    List<NameEntityDTO> findCharactersByMediaId(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(u.id, u.name)
        FROM MediaMetaData m
        JOIN m.universes u
        WHERE m.id = :mediaId
            AND m.userId = :userId
    """)
    List<NameEntityDTO> findUniversesByMediaId(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(t.id, t.name)
        FROM MediaMetaData m
        JOIN m.tags t
        WHERE m.id = :mediaId
            AND m.userId = :userId
    """)
    List<NameEntityDTO> findTagsByMediaId(@Param("userId") long userId, @Param("mediaId") long mediaId);


    @Transactional
    @Modifying
    @Query("""
        UPDATE MediaMetaData m
        SET m.preview = :preview
        WHERE m.id = :mediaId
            AND m.userId = :userId
    """)
    int updateMediaPreview(@Param("userId") long userId, @Param("mediaId") long mediaId, @Param("preview") String preview);


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.authors_media (media_id, authors_id)
        VALUES (:mediaId, :authorId)
    """, nativeQuery = true)
    int addAuthorToMedia(long mediaId, long authorId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.authors_media (media_id, authors_id)
        SELECT :mediaId, unnest(CAST(:authorIds AS bigint[]))
    """, nativeQuery = true)
    int addAuthorsToMedia(@Param("mediaId") long mediaId, @Param("authorIds") Long[] authorIds);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.characters_media (media_id, characters_id)
        VALUES (:mediaId, :characterId)
    """, nativeQuery = true)
    int addCharacterToMedia(long mediaId, long characterId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.characters_media (media_id, characters_id)
        SELECT :mediaId, unnest(CAST(:characterIds AS bigint[]))
    """, nativeQuery = true)
    int addCharactersToMedia(@Param("mediaId") long mediaId, @Param("characterIds") Long[] characterIds);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.universes_media (media_id, universes_id)
        VALUES (:mediaId, :universeId)
    """, nativeQuery = true)
    int addUniverseToMedia(long mediaId, long universeId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.universes_media (media_id, universes_id)
        SELECT :mediaId, unnest(CAST(:universeIds AS bigint[]))
    """, nativeQuery = true)
    int addUniversesToMedia(@Param("mediaId") long mediaId, @Param("universeIds") Long[] universeIds);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.tags_media (media_id, tags_id)
        VALUES (:mediaId, :tagId)
    """, nativeQuery = true)
    int addTagToMedia(long mediaId, long tagId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media.tags_media (media_id, tags_id)
        SELECT :mediaId, unnest(CAST(:tagIds AS bigint[]))
    """, nativeQuery = true)
    int addTagsToMedia(@Param("mediaId") long mediaId, @Param("tagIds") Long[] tagIds);


    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.authors_media am
        WHERE am.media_id = :mediaId
            AND am.authors_id = :authorId
    """, nativeQuery = true)
    int deleteAuthorFromMedia(@Param("mediaId") long mediaId, @Param("authorId") long authorId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.authors_media am
        WHERE am.media_id = :mediaId
            AND am.authors_id IN :authorIds
    """, nativeQuery = true)
    int deleteAuthorsFromMedia(@Param("mediaId") long mediaId, @Param("authorIds") Long[] authorIds);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.characters_media cm
        WHERE cm.media_id = :mediaId
            AND cm.characters_id = :characterId
    """, nativeQuery = true)
    int deleteCharacterFromMedia(@Param("mediaId") long mediaId, @Param("characterId") long characterId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.characters_media cm
        WHERE cm.media_id = :mediaId
            AND cm.characters_id IN :characterIds
    """, nativeQuery = true)
    int deleteCharactersFromMedia(@Param("mediaId") long mediaId, @Param("characterIds") Long[] characterIds);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.universes_media um
        WHERE um.media_id = :mediaId
            AND um.universes_id = :universeId
    """, nativeQuery = true)
    int deleteUniverseFromMedia(@Param("mediaId") long mediaId, @Param("universeId") long universeId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.universes_media cm
        WHERE cm.media_id = :mediaId
            AND cm.universes_id IN :universeIds
    """, nativeQuery = true)
    int deleteUniversesFromMedia(@Param("mediaId") long mediaId, @Param("universeIds") Long[] universeIds);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.tags_media tm
        WHERE tm.media_id = :mediaId
            AND tm.tags_id = :tagId
    """, nativeQuery = true)
    int deleteTagFromMedia(@Param("mediaId") long mediaId, @Param("tagId") long tagId);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM media.tags_media tm
        WHERE tm.media_id = :mediaId
            AND tm.tags_id IN :tagIds
    """, nativeQuery = true)
    int deleteTagsFromMedia(@Param("mediaId") long mediaId, @Param("tagIds") Long[] tagIds);


    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.authors
        SET length = length - 1
        WHERE id IN (
            SELECT authors_id
            FROM media.authors_media
            WHERE media_id = :mediaId
                AND user_id = :userId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementAuthorLengths(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.characters
        SET length = length - 1
        WHERE id IN (
            SELECT characters_id
            FROM media.characters_media
            WHERE media_id = :mediaId
                AND user_id = :userId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementCharacterLengths(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.universes
        SET length = length - 1
        WHERE id IN (
            SELECT universes_id
            FROM media.universes_media
            WHERE media_id = :mediaId
                AND user_id = :userId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementUniverseLengths(@Param("userId") long userId, @Param("mediaId") long mediaId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media.tags
        SET length = length - 1
        WHERE id IN (
            SELECT tags_id
            FROM media.tags_media
            WHERE media_id = :mediaId
                ANd user_id = :userId
        )
        AND length > 0
    """, nativeQuery = true)
    int decrementTagLengths(@Param("userId") long userId, @Param("mediaId") long mediaId);
}
