package dev.chinh.streamingservice.filemanager.service;

import com.github.benmanes.caffeine.cache.Cache;
import dev.chinh.streamingservice.filemanager.config.ApplicationConfig;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DirectoryCacheService {

    private final MongoTemplate mongoTemplate;
    private final MongoTemplate safeWriteMongoTemplate;

    private final Cache<String, ApplicationConfig.EntryCached> directoryIdCache;
    private final FileCacheService fileCacheService;

    public String getCachedElseDbDirectoryId(String parentId, String dirName, String userId, boolean mustBeDirectory) {
        String dirKey = getDirKey(dirName, parentId);
        var cached = (ApplicationConfig.DirectoryCached) directoryIdCache.asMap().get(dirKey);
        if (cached == null) {
            Query query = Query.query(Criteria
                    .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                    .and(FileItemField.PARENT_ID).is(parentId)
                    .and(FileItemField.NAME).is(dirName)
            );
            Update update = new Update().set(FileItemField.STATUS_CODE, FileStatus.IN_USE.getValue());
            FileSystemItem dir = safeWriteMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), FileSystemItem.class);
            if (dir == null)
                return null;
            if (mustBeDirectory && FileType.isNotDir(dir.getFileType())) {
                removeFileStatus(dir.getId());
                throw new IllegalArgumentException("File with name: " + dirName + " already exist but is not a directory");
            }
            fileCacheService.invalidateFileCache(dir.getId());
            directoryIdCache.asMap().computeIfAbsent(dirKey, k -> {
                Set<String> users = ConcurrentHashMap.newKeySet();
                users.add(userId);
                return new ApplicationConfig.DirectoryCached(dir.getId(), users);
            });
            return dir.getId();
        }
        return cached.dirId();
    }

    public ApplicationConfig.DirectoryCached getCachedOrCreateDirectory(String dirName, String dirParentId, String dirPath, String userId) {
        String dirKey = getDirKey(dirName, dirParentId);
        ApplicationConfig.EntryCached entry = directoryIdCache.get(dirKey, k -> {
            // Only executes if the key is missing
            String fileId = getOrCreateFolder(userId, dirName, dirParentId, dirPath, FileType.DIR);
            return new ApplicationConfig.DirectoryCached(fileId, ConcurrentHashMap.newKeySet());
        });

        if (entry instanceof ApplicationConfig.DirectoryCached directoryCached) {
            // Thread-safe mutation of the set
            directoryCached.userUsing().add(userId);
            return directoryCached;
        }

        throw new IllegalStateException("Unexpected cache entry type for key: " + dirKey);
    }

    private String getOrCreateFolder(String userId, String name, String parentId, String currentPath, FileType fileType) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name)
        );

        // setOnInsert to create if not exists
        Update update = new Update()
                .setOnInsert(FileItemField.USER_ID, Long.parseLong(userId))
                .setOnInsert(FileItemField.NAME, name)
                .setOnInsert(FileItemField.PARENT_ID, parentId)
                .setOnInsert(FileItemField.PATH, currentPath)
                .setOnInsert(FileItemField.FILE_TYPE, fileType)
                .setOnInsert(FileItemField.STATUS_CODE, FileStatus.IN_USE.getValue())
                .setOnInsert(FileItemField.UPLOAD_DATE, LocalDateTime.now());

        // upsert to create and return in one operation - atomic
        FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);

        FileSystemItem dir = mongoTemplate.findAndModify(query, update, options, FileSystemItem.class);

        if (dir == null) throw new RuntimeException("Failed to create folder");

        return dir.getId();
    }

    public void addDirectoryToUserUsingList(String userId, String dirName, String dirParentId) {
        String dirKey = getDirKey(dirName, dirParentId);
        // .get() is atomic for creation, and returns the existing item if present
        ApplicationConfig.EntryCached entry = directoryIdCache.get(userId, k ->
                new ApplicationConfig.UserDirUsing(ConcurrentHashMap.newKeySet())
        );

        if (entry instanceof ApplicationConfig.UserDirUsing(Set<String> dirUserUsing)) {
            // Mutate the thread-safe set directly without replacing the cache record!
            dirUserUsing.add(dirKey);
        }
    }

    public void removeAllDirectoriesUserUsing(String userId) {
        // Get the value safely WITHOUT locking the map in a compute block
        ApplicationConfig.EntryCached value = directoryIdCache.getIfPresent(userId);
        if (value instanceof ApplicationConfig.UserDirUsing(Set<String> dirUserUsing)) {
            // Invalidate the user immediately so no new directories are added to them
            directoryIdCache.invalidate(userId);
            // Iterate over the set outside of any parent locks
            for (String dirKey : dirUserUsing) {
                directoryIdCache.asMap().computeIfPresent(dirKey, (_, dirValue) -> {
                    var dirCached = (ApplicationConfig.DirectoryCached) dirValue;
                    dirCached.userUsing().remove(userId);

                    if (dirCached.userUsing().isEmpty()) {
                        removeFileStatus(dirCached.dirId());
                        return null; // Return null to remove the directory key
                    }
                    return dirValue;
                });
            }
        }
    }

    private void removeFileStatus(String fileId) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().unset(FileItemField.STATUS_CODE);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileId);
    }

    private String getDirKey(String dirName, String parentId) {
        return "DIR_" + dirName + "|" + parentId;
    }

}
