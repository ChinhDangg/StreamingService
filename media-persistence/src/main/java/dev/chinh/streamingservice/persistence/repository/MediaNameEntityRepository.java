package dev.chinh.streamingservice.persistence.repository;

import dev.chinh.streamingservice.persistence.entity.MediaNameEntity;
import dev.chinh.streamingservice.persistence.projection.MediaNameEntry;
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

    @Query("SELECT e.name FROM #{#entityName} e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<String> findNamesContaining(@Param("name") String name);

    @Query("SELECT new dev.chinh.streamingservice.persistence.projection.MediaNameEntry(e.name, e.length) FROM #{#entityName} e")
    Page<MediaNameEntry> findAllNames(Pageable pageable);

    @Query("SELECT e FROM #{#entityName} e WHERE LOWER(e.name) LIKE :name")
    Optional<MediaNameEntity> findByName(String name);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE #{#entityName} e
        SET e.length = e.length + 1
        WHERE e.id = :id
    """)
    int incrementLength(@Param("id") long id);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE #{#entityName} e
        SET e.length = e.length - 1
        WHERE e.id = :id
          AND e.length > 0
    """)
    int decrementLength(@Param("id") long id);

    @Query("SELECT e.name FROM #{#entityName} e WHERE e.id = :id")
    String getNameEntityName(@Param("id") long id);
}
