package dev.chinh.streamingservice.filemanager.repository;

import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    Slice<FileSystemItem> findByParentId(String parentId, Pageable pageable);

    Slice<FileSystemItem> findByPath(String path, Pageable pageable);

    Slice<FileSystemItem> findByPathStartingWith(String path, Pageable pageable);
}
