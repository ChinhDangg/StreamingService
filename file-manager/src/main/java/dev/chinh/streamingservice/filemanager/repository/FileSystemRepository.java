package dev.chinh.streamingservice.filemanager.repository;

import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    Slice<FileSystemItem> findByUserIdAndParentId(Long userId, String parentId, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndPath(Long userId, String path, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndPathStartingWith(Long userId, String path, Pageable pageable);
}
