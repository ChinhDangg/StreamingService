package dev.chinh.streamingservice.mediapersistence.repository;

import dev.chinh.streamingservice.mediapersistence.entity.MediaNameEntity;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface MediaNameEntityRepository<T extends MediaNameEntity, ID> extends JpaRepository<T, ID> {

    @Query("""
        SELECT new dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO(e.id, e.name, e.length)
        FROM #{#entityName} e
        WHERE e.userId = :userId
    """)
    Page<NameEntityDTO> findAllNames(@Param("userId") long userId, Pageable pageable);

    @Query("""
        SELECT e.id
        FROM #{#entityName} e
        WHERE e.userId = :userId
            AND e.id IN :ids
    """)
    List<Long> findIdByUserIdAndIdIn(@Param("userId") long userId, @Param("ids") List<Long> ids);

    Optional<T> findByIdAndUserId(ID id, long userId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE #{#entityName} e
        SET e.length = e.length + 1
        WHERE e.id IN :ids
            AND e.userId = :userId
    """)
    int incrementLength(@Param("userId") long userId, @Param("ids") Long[] ids);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE #{#entityName} e
        SET e.length = e.length - 1
        WHERE e.id IN :ids
            AND e.length > 0
            AND e.userId = :userId
    """)
    int decrementLength(@Param("userId") long userId, @Param("ids") Long[] ids);

    @Query("""
        SELECT e.name
        FROM #{#entityName} e
        WHERE e.id = :id
            ANd e.userId = :userId
    """)
    String getNameEntityNameById(@Param("userId") long userId, @Param("id") long id);

    void deleteByIdAndUserId(ID id, long userId);

    Optional<T> findByUserIdAndName(long userId, String name);
}
