package dev.chinh.streamingservice.filemanager.repository;

import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;


public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    @Query("{ 'userId': ?0, 'path': { $regex: ?1 } }")
    Slice<FileSystemItem> findByUserIdAndPathRegex(Long userId, String pathRegex, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndParentId(Long userId, String parentId, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndPath(Long userId, String path, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndPathStartingWith(Long userId, String path, Pageable pageable);
}
