package dev.chinh.streamingservice.filemanager;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileSystemRepository extends MongoRepository<FileSystemItem, String> {

    List<FileSystemItem> findByParentId(String parentId);

    List<FileSystemItem> findByPath(String path);

    List<FileSystemItem> findByPathStartingWith(String path);

    void updateMId(String id, Long mId);
}
