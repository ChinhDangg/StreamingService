package dev.chinh.streamingservice.filemanager.service;

import com.github.benmanes.caffeine.cache.Cache;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileCacheService {

    /*
        This service is used to cache file metadata in memory for general read not maintaining locked dir item - that is directory cache service
     */

    private final Cache<String, FileSystemItem> fileCache;
    private final MongoTemplate mongoTemplate;

    public List<FileSystemItem> getCachedFilesElseFromDatabase(Collection<String> ids, Criteria criteria) {
        Map<String, FileSystemItem> result = fileCache.getAll(ids, (keysToFetch) -> {
            Query query = new Query(Criteria.where("id").in(keysToFetch));
            if (criteria != null) {
                query.addCriteria(criteria);
            }
            List<FileSystemItem> fetched = mongoTemplate.find(query, FileSystemItem.class);
            return fetched.stream().collect(Collectors.toMap(FileSystemItem::getId, item -> item));
        });
        return new ArrayList<>(result.values());
    }

    public FileSystemItem getFileCache(String id) {
        return fileCache.getIfPresent(id);
    }

    public FileSystemItem getFileCache(String id, Function<String, FileSystemItem> function) {
        return fileCache.get(id, function);
    }

    public FileSystemItem getFileCacheElseFromDatabase(String userId, String id) {
        // atomic: if multiple threads request the same ID, the compute function runs only once.
        // get, else compute, save, and return the result.
        return fileCache.get(id, k -> findById(userId, k));
    }

    public void invalidateFileCache(String id) {
        fileCache.invalidate(id);
    }

    public FileSystemItem findById(String userId, String id) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and("id").is(id)
        );
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }
}
