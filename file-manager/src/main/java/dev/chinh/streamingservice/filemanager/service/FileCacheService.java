package dev.chinh.streamingservice.filemanager.service;

import com.github.benmanes.caffeine.cache.Cache;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileCacheService {

    /*
        This service is used to cache file metadata in memory for general read not maintaining locked dir item - that is directory cache service
        If multiple service of file-manager- move to distributed cache like redis!
     */

    private final Cache<String, FileSystemItem> fileCache;

    // this does not check the items belong to a userId or not, use only after checking all ids belong to the userId
    public List<FileSystemItem> getCachedFilesElse(Collection<String> ids, Predicate<FileSystemItem> filter, Function<Set<? extends String>, List<FileSystemItem>> fetcher) {
        Map<String, FileSystemItem> result = fileCache.getAll(ids, (keysToFetch) -> {
            List<FileSystemItem> fetched = fetcher.apply(keysToFetch);
            return fetched.stream().collect(Collectors.toMap(FileSystemItem::getId, item -> item));
        });
        var listResult = new ArrayList<>(result.values());
        if (filter != null)
            return listResult.stream().filter(filter).toList();
        return listResult;
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
    public FileSystemItem getCachedFileElse(String userId, String id, boolean getCachedFirst, Function<String, FileSystemItem> fetcher) {
        // atomic: if multiple threads request the same ID, the compute function runs only once.
        // get, else compute, save, and return the result.
        if (getCachedFirst) {
            var item = fileCache.get(id, fetcher);
            if (item.getUserId() == null || item.getUserId().toString().equals(userId))
                return item;
            return null;
        }
        var item = fetcher.apply(id);
        putFileCache(item);
        return item;
    }

    public void putFileCache(FileSystemItem item) {
        fileCache.put(item.getId(), item);
    }

    public void invalidateFileCache(String id) {
        fileCache.invalidate(id);
    }

    public void invalidateFileCache(Collection<String> ids) {
        fileCache.invalidateAll(ids);
    }

    public void invalidateAllFileCache() {
        fileCache.invalidateAll();
    }
}
