package dev.chinh.streamingservice.filemanager.repository;

import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;


public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    @Query("{ 'userId': ?0, 'path': { $regex: ?1 } }")
    Slice<FileSystemItem> findByUserIdAndPathRegex(Long userId, String pathRegex, Pageable pageable);

    Slice<FileSystemItem> findByUserIdAndParentId(Long userId, String parentId, Pageable pageable);

    @Query("{ 'userId': ?0, 'path': { $regex: ?1 } }")
    Window<FileSystemItem> findByUserIdAndPathRegex(Long userId, String pathRegex, Sort sort, Limit limit, ScrollPosition scrollPosition);

    Window<FileSystemItem> findByUserIdAndParentId(Long userId, String parentId, Sort sort, Limit limit, ScrollPosition scrollPosition);
}
