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

    private final Cache<String, ApplicationConfig.EntryCached> directoryIdCache;

    public String getCachedElseDbDirectoryId(String parentId, String dirName, String userId) {
        String dirKey = getDirKey(dirName, parentId);
        var cached = (ApplicationConfig.DirectoryCached) directoryIdCache.asMap().get(dirKey);
        if (cached == null) {
            Query query = Query.query(Criteria
                    .where(FileItemField.PARENT_ID).is(parentId)
                    .and(FileItemField.NAME).is(dirName));
            Update update = new Update().set(FileItemField.STATUS_CODE, FileStatus.IN_USE.getValue());
            FileSystemItem dir = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), FileSystemItem.class);
            if (dir == null)
                return null;
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
        return (ApplicationConfig.DirectoryCached) directoryIdCache.asMap().compute(dirKey, (_, existing) -> {
            if (existing == null) {
                String fileId = getOrCreateFolder(dirName, dirParentId, dirPath, FileType.DIR);
                Set<String> users = ConcurrentHashMap.newKeySet();
                users.add(userId);
                return new ApplicationConfig.DirectoryCached(fileId, users);
            } else {
                ((ApplicationConfig.DirectoryCached) existing).userUsing().add(userId);
                return existing;
            }
        });
    }

    private String getOrCreateFolder(String name, String parentId, String currentPath, FileType fileType) {
        Query query = new Query(Criteria
                .where(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name)
                .and(FileItemField.FILE_TYPE).in(FileType.DIR, FileType.ALBUM)
        );

        // setOnInsert to create if not exists
        Update update = new Update()
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
        directoryIdCache.asMap().compute(userId, (_, existing) -> {
            if (existing == null) {
                Set<String> dirUserUsing = ConcurrentHashMap.newKeySet();
                dirUserUsing.add(dirKey); // add dirKey to get dir info from cache back (not using the dirId)
                return new ApplicationConfig.UserDirUsing(dirUserUsing);
            } else {
                ((ApplicationConfig.UserDirUsing) existing).dirUserUsing().add(dirKey);
                return existing;
            }
        });
    }

    public void removeAllDirectoriesUserUsing(String userId) {
        directoryIdCache.asMap().computeIfPresent(userId, (_, value) -> {
            for (String dirKey : ((ApplicationConfig.UserDirUsing) value).dirUserUsing()) {
                directoryIdCache.asMap().computeIfPresent(dirKey, (_, dirValue) -> {
                    var dirCached = (ApplicationConfig.DirectoryCached) dirValue;
                    dirCached.userUsing().remove(userId);

                    if (dirCached.userUsing().isEmpty()) {
                        removeFileStatus(dirCached.dirId());
                        return null; // null to remove the key
                    }
                    return dirValue;
                });
            }
            return null;
        });
    }

    private void removeFileStatus(String fileId) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().unset(FileItemField.STATUS_CODE);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }

    private String getDirKey(String dirName, String parentId) {
        return "DIR_" + dirName + "|" + parentId;
    }

}
