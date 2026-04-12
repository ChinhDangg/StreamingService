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

    /**
     * If getCachedFirst is true, the file cache is checked first. Then from the database. The result is cached.
     * Else from database only, the result is still cached.
     */
    public FileSystemItem getFileCacheElseFromDatabase(String userId, String id, boolean getCachedFirst) {
        // atomic: if multiple threads request the same ID, the compute function runs only once.
        // get, else compute, save, and return the result.
        if (getCachedFirst)
            return fileCache.get(id, k -> findById(userId, k));
        var item = findById(userId, id);
        putFileCache(item);
        return item;
    }

    public void putFileCache(FileSystemItem item) {
        fileCache.put(item.getId(), item);
    }

    public void invalidateFileCache(String id) {
        fileCache.invalidate(id);
    }

    private FileSystemItem findById(String userId, String id) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and("id").is(id)
        );
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }
}
