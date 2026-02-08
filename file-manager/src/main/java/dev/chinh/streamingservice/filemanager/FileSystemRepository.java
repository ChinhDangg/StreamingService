package dev.chinh.streamingservice.filemanager;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileSystemRepository extends MongoRepository<FileSystemItem, Long> {

    List<FileSystemItem> findByPath(String path);

    List<FileSystemItem> findByPathStartingWith(String path);
}
