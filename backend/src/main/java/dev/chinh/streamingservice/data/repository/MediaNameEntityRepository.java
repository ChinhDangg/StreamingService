package dev.chinh.streamingservice.data.repository;

import dev.chinh.streamingservice.data.entity.MediaNameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MediaNameEntityRepository<T extends MediaNameEntity, ID> extends JpaRepository<T, ID> {

    @Query("SELECT e.name FROM #{#entityName} e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<String> findNamesContaining(@Param("name") String name);
}
