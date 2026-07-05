package dev.chinh.streamingservice.filemanager.service;

import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FolderLocks;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FileLockService {

    private final MongoTemplate safeWriteMongoTemplate;

    public void lockFileItem(String userId, Set<String> fileIds, Map<String, FileStatus> statusMap) {
        if (fileIds.isEmpty()) return;
        // Use UNORDERED to process everything even if some IDs already exist
        BulkOperations bulkOps = safeWriteMongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, FolderLocks.class);

        for (String id : fileIds) {
            Query query = new Query(Criteria.where("id").is(id));
            Update update = new Update()
                    .setOnInsert(FileItemField.USER_ID, userId)
                    .setOnInsert(FileItemField.STATUS_CODE, statusMap.getOrDefault(id, FileStatus.PROCESSING))
                    .set("expiryTime", new Date());
            // use setOnInsert, if the ID exists, nothing happens.
            // If it doesn't exist, a new document is created with these values.
            bulkOps.upsert(query, update);
        }
        bulkOps.execute();
    }

    public void releaseLockedFileItem(Set<String> fileIds) {
        if (fileIds.isEmpty()) return;
        Query query = new Query(Criteria.where("id").in(fileIds));
        safeWriteMongoTemplate.remove(query, FolderLocks.class);
    }

    public FolderLocks checkIfFileItemInLock(Set<String> fileIds, Map<String, Set<FileStatus>> ignoredStatusMap) {
        if (fileIds.isEmpty()) return null;
        Criteria criteria = Criteria.where("id").in(fileIds);
        if (ignoredStatusMap != null && !ignoredStatusMap.isEmpty()) {
            List<Criteria> excludeCriteria = new ArrayList<>();
            ignoredStatusMap.forEach((id, status) -> {
                excludeCriteria.add(Criteria.where("id").is(id).and(FileItemField.STATUS_CODE).in(status));
            });
            // "nor" ensures that none of these specific ID+Status pairs are returned
            criteria.norOperator(excludeCriteria.toArray(new Criteria[0]));
        }
        return safeWriteMongoTemplate.findOne(new Query(criteria), FolderLocks.class);
    }
}
