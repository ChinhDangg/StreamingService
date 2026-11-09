package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.data.FolderLocks;
import dev.chinh.streamingservice.filemanager.event.FileEventProducer;
import dev.chinh.streamingservice.filemanager.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileTrashService {

    public static final String TRASH_NAME = "Trash";
    public static final String TRASH_PATH_NAME = "__TRASH__";
    public static final String TRASH_PATH = "/" + TRASH_PATH_NAME + "/";
    public static final String TRASH_START_POSTFIX = ".trash_";
    private static String TRASH_FOLDER_ID = null;

    private final FileRepository fileRepository;

    private final ApplicationEventPublisher publisher;

    private final FileUtilService fileUtilService;
    private final FileLockService fileLockService;
    private final FileCacheService fileCacheService;

    @Transactional
    public List<String> moveToTrash(String userId, List<String> fileIds) {
        Set<String> notCheckedIds = new HashSet<>(fileIds);

        Query query = new Query(Criteria
                .where(FileItemField.ID).in(notCheckedIds)
                .and(FileItemField.USER_ID).is(Long.parseLong(userId))
        );
        query.fields().include(FileItemField.ID, FileItemField.PARENT_ID, FileItemField.NAME, FileItemField.PATH);
        List<FileSystemItem> items = fileRepository.find(query);

        StringBuilder allNotInTrashParentItemPaths = new StringBuilder();
        List<String> alreadyInTrash = new ArrayList<>();
        Set<String> notInTrash = new HashSet<>();
        HashMap<String, FileSystemItem> itemsMap = new HashMap<>(items.size());
        for (FileSystemItem item : items) {
            notCheckedIds.remove(item.getId());
            itemsMap.put(item.getId(), item);
            if (item.getParentId().equals(getTRASH_FOLDER_ID()))
                alreadyInTrash.add(item.getId());
            else {
                notInTrash.add(item.getId());
                allNotInTrashParentItemPaths.append(item.getPath());
            }
        }

        if (notInTrash.isEmpty())
            return List.of("All files are already in trash");

        Set<String> allParentIdsInPaths = fileUtilService.getCommonIds(allNotInTrashParentItemPaths.toString());
        Map<String, Set<FileStatus>> parentIgnoreStatus = allParentIdsInPaths.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        _ -> Set.of(FileStatus.BEING_MOVED_INTO),
                        (existing, replacement) -> existing
                ));
        Set<String> all = new HashSet<>(allParentIdsInPaths);
        all.addAll(notInTrash);
        List<FolderLocks> folderIsLocked = fileLockService.checkIfFileItemsInLock(all, parentIgnoreStatus);

        List<String> lockedFileMessage = new ArrayList<>();
        if (!folderIsLocked.isEmpty()) {
            Map<String, FolderLocks> lockedIdsMap = folderIsLocked.stream().collect(Collectors.toMap(FolderLocks::getId, f -> f));
            notInTrash.removeIf(id -> {
                Optional<String> firstMatchingId = fileUtilService.getCommonIds(itemsMap.get(id).getPath() + id)
                        .stream()
                        .filter(lockedIdsMap::containsKey)
                        .findFirst();
                if (firstMatchingId.isPresent()) {
                    String lockedFileId = firstMatchingId.get();
                    lockedFileMessage.add("Unable to trash file " + itemsMap.get(id).getName() + " as " + lockedFileId + " in path is " + lockedIdsMap.get(lockedFileId).getStatusCode());
                    return true;
                }
                return false;
            });
        }

        if (!notInTrash.isEmpty()) {
            fileLockService.lockFileItem(userId, notInTrash, Collections.emptyMap());
            fileCacheService.invalidateFileCache(notInTrash);

            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    userId,
                    new MediaUpdateEvent.FilesMovedToTrash(userId, notInTrash)
            ));
        }

        List<String> message = new ArrayList<>(List.of("Moving " + notInTrash.size() + " files to trash"));
        if (!alreadyInTrash.isEmpty())
            message.add(alreadyInTrash.size() + " files are already in trash");
        if (!notCheckedIds.isEmpty())
            message.add(notCheckedIds.size() + " files not found");
        if (!lockedFileMessage.isEmpty())
            message.addAll(lockedFileMessage);
        return message;
    }

    // file can be restored to the target parent if present else back to its old saved directory
    @Transactional
    public List<String> moveOutOfTrash(String userId, List<String> fileIds, Optional<String> targetParent) {
        Set<String> notCheckedIds = new HashSet<>(fileIds);

        Query query = new Query(Criteria
                .where(FileItemField.ID).in(notCheckedIds)
                .and(FileItemField.USER_ID).is(Long.parseLong(userId))
        );
        query.fields().include(FileItemField.ID, FileItemField.PARENT_ID, FileItemField.NAME, FileItemField.PATH, FileItemField.OLD_PARENT_ID);
        List<FileSystemItem> items = fileRepository.find(query);

        StringBuilder allInTrashParentItemPaths = new StringBuilder();
        Set<String> alreadyInTrash = new HashSet<>();
        Set<String> notInTrash = new HashSet<>();
        HashMap<String, FileSystemItem> itemsMap = new HashMap<>(items.size());
        for (FileSystemItem item : items) {
            notCheckedIds.remove(item.getId());
            itemsMap.put(item.getId(), item);
            if (item.getParentId().equals(getTRASH_FOLDER_ID())) {
                alreadyInTrash.add(item.getId());
                allInTrashParentItemPaths.append(item.getPath());
            }
            else
                notInTrash.add(item.getId());
        }

        if (alreadyInTrash.isEmpty())
            return List.of("No files are in trash");

        Set<String> allParentIdsInPaths = fileUtilService.getCommonIds(allInTrashParentItemPaths.toString());
        Map<String, Set<FileStatus>> parentIgnoreStatus = allParentIdsInPaths.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        _ -> Set.of(FileStatus.BEING_MOVED_INTO),
                        (existing, replacement) -> existing
                ));
        Set<String> all = new HashSet<>(allParentIdsInPaths);
        all.addAll(alreadyInTrash);
        List<FolderLocks> folderIsLocked = fileLockService.checkIfFileItemsInLock(all, parentIgnoreStatus);

        List<String> lockedFileMessage = new ArrayList<>();
        if (!folderIsLocked.isEmpty()) {
            Map<String, FolderLocks> lockedIdsMap = folderIsLocked.stream().collect(Collectors.toMap(FolderLocks::getId, f -> f));
            alreadyInTrash.removeIf(id -> {
                Optional<String> firstMatchingId = fileUtilService.getCommonIds(itemsMap.get(id).getPath() + id)
                        .stream()
                        .filter(lockedIdsMap::containsKey)
                        .findFirst();
                if (firstMatchingId.isPresent()) {
                    String lockedFileId = firstMatchingId.get();
                    lockedFileMessage.add("Unable to move file out of trash: " + itemsMap.get(id).getName() + " as " + lockedFileId + " in path is " + lockedIdsMap.get(lockedFileId).getStatusCode());
                    return true;
                }
                return false;
            });
        }

        List<String> destinationNameConflictMessage = new ArrayList<>();
        var targetParentFileItem = targetParent.map(s -> fileUtilService.findById(userId, s, true)).orElse(null);
        alreadyInTrash.removeIf(id -> {
            String currentTrashName = itemsMap.get(id).getName();
            String originalName = currentTrashName.substring(0, (currentTrashName.length() - TRASH_START_POSTFIX.length() - id.length()));
            boolean sameNameExisted = fileUtilService.itemWithNameExists(
                    userId,
                    targetParentFileItem == null ? itemsMap.get(id).getOldParentId() : targetParentFileItem.getId(),
                    originalName
            );
            if (sameNameExisted) {
                destinationNameConflictMessage.add("Destination directory already has same name item: " + originalName);
                return true;
            }
            return false;
        });

        if (!alreadyInTrash.isEmpty()) {
            fileLockService.lockFileItem(userId, alreadyInTrash, Collections.emptyMap());
            fileCacheService.invalidateFileCache(alreadyInTrash);

            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    userId,
                    new MediaUpdateEvent.FilesMovedOutOfTrash(userId, alreadyInTrash, targetParent)
            ));
        }

        List<String> message = new ArrayList<>(List.of("Moving " + alreadyInTrash.size() + " files out of trash"));
        if (!notInTrash.isEmpty())
            message.add(notInTrash.size() + " files are not in trash");
        if (!notCheckedIds.isEmpty())
            message.add(notCheckedIds.size() + " files not found");
        if (!lockedFileMessage.isEmpty())
            message.addAll(lockedFileMessage);
        if (!destinationNameConflictMessage.isEmpty())
            message.addAll(destinationNameConflictMessage);
        return message;
    }

    @Transactional
    public void initiateDeleteFile(String userId, String fileId) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, false);

        if (!item.getParentId().equals(getTRASH_FOLDER_ID())) {
            throw new IllegalArgumentException("File is not in trash");
        }

        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            boolean anyChildMedia = anyChildMedia(userId, fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
            if (anyChildMedia) {
                throw new IllegalArgumentException("Directory is not empty - include media item");
            }
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        fileLockService.lockFileItem(userId, Set.of(item.getId()), Map.of(item.getId(), FileStatus.DELETING));
        fileCacheService.invalidateFileCache(item.getId());

        if (item.getMId() != null && item.getMId() > 0) {
            throw new IllegalArgumentException("File is already marked as media - delete through media file item instead: " + item.getMId());
        }

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                userId,
                new MediaUpdateEvent.FileDeleted(userId,
                        item.getId(),
                        fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(item, true)),
                        FileType.isNotDir(item.getFileType()),
                        FileType.convertFileTypeToMediaType(item.getFileType()),
                        null
                )
        ));
    }

    @Transactional
    public void initiateDeleteMediaFile(String userId, long mediaId) {
        FileSystemItem item = fileUtilService.findByMId(userId, mediaId);
        if (item == null) {
            throw new IllegalArgumentException("Media file not found: " + mediaId);
        }
        if (!item.getParentId().equals(getTRASH_FOLDER_ID())) {
            throw new IllegalArgumentException("Media File is not in trash");
        }

        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            boolean anyChildMedia = anyChildMedia(userId, fileUtilService.getPathForFileItem(item.getPath(), item.getId()));
            if (anyChildMedia) {
                throw new IllegalArgumentException("Media is not empty - include nested media item");
            }
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        fileLockService.lockFileItem(userId, Set.of(item.getId()), Map.of(item.getId(), FileStatus.DELETING));
        fileCacheService.invalidateFileCache(item.getId());

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_HANDLER_SEARCH_AND_BACKUP_TOPIC,
                userId,
                new MediaUpdateEvent.FileDeleted(
                        userId,
                        item.getId(),
                        fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(item, true)),
                        FileType.isNotDir(item.getFileType()),
                        FileType.convertFileTypeToMediaType(item.getFileType()),
                        item.getMId()
                )
        ));
    }

    private boolean anyChildMedia(String userId, String parentPath) {
        String quotedParentPath = Pattern.quote(parentPath);
        return fileRepository.checkExistWithUserId(userId, Criteria
                .where(FileItemField.PATH).regex("^" + quotedParentPath)
                .and(FileItemField.MEDIA_ID).nin(null, 0));
    }

    public boolean itemIsInTrash(String itemPath) {
        return FileUtilService.containsInPath(itemPath, getTRASH_FOLDER_ID());
    }

    public void createTrashFolder() {
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(TRASH_NAME)
                .and(FileItemField.PATH).is(TRASH_PATH)
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );

        Update update = new Update()
                .setOnInsert(FileItemField.NAME, TRASH_NAME)
                .setOnInsert(FileItemField.PATH, TRASH_PATH)
                .setOnInsert(FileItemField.FILE_TYPE, FileType.DIR);

        UpdateResult result = fileRepository.upsert(query, update);
        if (!result.wasAcknowledged()) throw new RuntimeException("Failed to create trash folder");
    }

    public String getTRASH_FOLDER_ID() {
        if (TRASH_FOLDER_ID != null) return TRASH_FOLDER_ID;
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(TRASH_NAME)
                .and(FileItemField.PATH).is(TRASH_PATH)
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );
        FileSystemItem item = fileRepository.findOne(query);

        if (item == null) throw new RuntimeException("Failed to find trash folder");

        TRASH_FOLDER_ID = item.getId();
        return TRASH_FOLDER_ID;
    }

    public FileSystemItem getTrashFileSystemItem() {
        return fileCacheService.getFileCache(getTRASH_FOLDER_ID(), (trashDirId) -> {
            Query query = new Query(Criteria
                    .where("id").is(trashDirId)
            );
            return fileRepository.findOne(query);
        });
    }
}
