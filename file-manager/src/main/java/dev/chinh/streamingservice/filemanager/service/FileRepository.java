package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.repository.FileSystemItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FileRepository {

    private final FileSystemItemRepository fileSystemItemRepository;
    private final MongoTemplate mongoTemplate;
    private final FileCacheService fileCacheService;

    /// ----------- READ
    public boolean checkExistWithUserId(String userId, Criteria criteria) {
        Query query = new Query(Criteria.where(FileItemField.USER_ID).is(Long.parseLong(userId)));
        query.addCriteria(criteria);
        return mongoTemplate.exists(query, FileSystemItem.class);
    }

    public FileSystemItem findOne(Query query) {
        return mongoTemplate.findOne(query, FileSystemItem.class);
    }

    public List<FileSystemItem> find(Query query) {
        return mongoTemplate.find(query, FileSystemItem.class);
    }

    public AggregationResults<FileSystemItem> aggregate(Aggregation aggregation, String collectionName) {
        return mongoTemplate.aggregate(aggregation, collectionName, FileSystemItem.class);
    }

    public Window<FileSystemItem> findByUserIdAndPathRecursive(String userId, String path, Sort sort, Limit limit, ScrollPosition scrollPosition) {
        return fileSystemItemRepository.findByUserIdAndPathRegex(
                Long.parseLong(userId), path, sort, limit, scrollPosition
        );
    }

    public Window<FileSystemItem> findByUserIdAndParentId(String userId, String parentId, Sort sort, Limit limit, ScrollPosition scrollPosition) {
        return fileSystemItemRepository.findByUserIdAndParentId(
                Long.parseLong(userId), parentId, sort, limit, scrollPosition
        );
    }


    /// ----------- INSERT
    public FileSystemItem insert(FileSystemItem item) {
        return mongoTemplate.insert(item);
    }

    public UpdateResult upsert(Query query, Update update) {
        return mongoTemplate.upsert(query, update, FileSystemItem.class);
    }


    /// ----------- UPDATE
    public UpdateResult updateFirstByIdAndUserId(String userId, String fileId, Update update) {
        Query query = new Query(Criteria
                .where("id").is(fileId)
                .and(FileItemField.USER_ID).is(Long.parseLong(userId)));
        var result = mongoTemplate.updateFirst(query, update, FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileId);
        return result;
    }

    public FileSystemItem findAndModifyById(String fileId, Update update) {
        Query query = new Query(Criteria.where("id").is(fileId));
        var item = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), FileSystemItem.class);
        fileCacheService.invalidateFileCache(fileId);
        return item;
    }

    public FileSystemItem findAndModify(Query query, FindAndModifyOptions options, Update update) {
        var item = mongoTemplate.findAndModify(query, update, options, FileSystemItem.class);
        if (item == null) return null;
        fileCacheService.invalidateFileCache(item.getId());
        return item;
    }

    public void updateMulti(Query query, AggregationUpdate update) {
        mongoTemplate.updateMulti(query, update, FileSystemItem.class);
        fileCacheService.invalidateAllFileCache();
    }

    public void updateMulti(Collection<String> affectedIds, Query query, Update update) {
        mongoTemplate.updateMulti(query, update, FileSystemItem.class);
        fileCacheService.invalidateFileCache(affectedIds);
    }

    /// ----------- DELETE
    public void remove(Collection<String> ids) {
        mongoTemplate.remove(new Query(Criteria.where("id").in(ids)), FileSystemItem.class);
        fileCacheService.invalidateFileCache(ids);
    }

    public void remove(String id) {
        mongoTemplate.remove(new Query(Criteria.where("id").is(id)), FileSystemItem.class);
        fileCacheService.invalidateFileCache(id);
    }
}
