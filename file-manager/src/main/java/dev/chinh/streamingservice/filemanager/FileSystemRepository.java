package dev.chinh.streamingservice.filemanager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    Page<FileSystemItem> findByParentId(String parentId, Pageable pageable);

    Page<FileSystemItem> findByPath(String path, Pageable pageable);

    Page<FileSystemItem> findByPathStartingWith(String path, Pageable pageable);
}
