package dev.chinh.streamingservice.filemanager.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileUtilService {

    private final FileRepository fileRepository;
    private final FileCacheService fileCacheService;

    public static final String MEDIA_PATH = ContentMetaData.MEDIA_BUCKET;
    private static String ROOT_PATH = null;
    private static String ROOT_FOLDER_ID = null;

    public Set<String> getCommonIds(String idPath) {
        String[] idList = idPath.split("/");
        Set<String> commonIds = new HashSet<>(Arrays.asList(idList));
        commonIds.remove("");
        commonIds.remove(getROOT_FOLDER_ID());
        return commonIds;
    }

    public String getLockedInfoString(String userId, String fileId, FileStatus status) {
        return "File " + findById(userId, fileId, true).getName() + " is " + status;
    }

    public boolean itemWithNameExists(String userId, String parentId, String name) {
        return fileRepository.checkExistWithUserId(userId, Criteria
                .where(FileItemField.PARENT_ID).is(parentId)
                .and(FileItemField.NAME).is(name));
    }

    public String addUserIdToPathStart(String userId, String path) {
        if (path.startsWith(userId + "/")) return path;
        if (path.isBlank()) return userId;
        return path.startsWith("/") ? userId + path : userId + "/" + path;
    }

    public String addUserIdToPathEnd(String userId, String path) {
        if (path.endsWith("/" + userId)) return path;
        if (path.isBlank()) return userId;
        return path.endsWith("/") ? path + userId : path + "/" + userId;
    }

    public String getFullPathInName(FileSystemItem item, boolean omitRoot) {
        String pathInId = item.getPath();
        List<String> pathIds = Arrays.stream(pathInId.split("/"))
                .filter(s -> !s.isEmpty() && (!omitRoot || !s.equals(getROOT_FOLDER_ID())))
                .toList();

        if (pathIds.isEmpty()) {
            if (omitRoot && item.getId().equals(getROOT_FOLDER_ID()))
                return "";
            return item.getName();
        }

        List<FileSystemItem> parents = getItemInIds(pathIds, true, null, null);

        Map<String, String> nameMap = parents.stream().collect(Collectors.toMap(FileSystemItem::getId, FileSystemItem::getName));

        return pathIds.stream()
                .map(id -> nameMap.getOrDefault(id, "Unknown"))
                .collect(Collectors.joining("/")) + "/" + item.getName();
    }

    public String getFullPathInName(String pathInId, boolean omitRoot, Optional<Map<String, String>> nameHelper) {
        List<String> pathIds = Arrays.stream(pathInId.split("/"))
                .filter(s -> !s.isEmpty() && (!omitRoot || !s.equals(getROOT_FOLDER_ID())))
                .toList();

        List<FileSystemItem> parents = getItemInIds(pathIds, true, null, null);
        Map<String, String> nameMap = parents.stream().collect(Collectors.toMap(FileSystemItem::getId, FileSystemItem::getName));
        nameHelper.ifPresent(nameMap::putAll);

        return pathIds.stream()
                .map(id -> nameMap.getOrDefault(id, "Unknown"))
                .collect(Collectors.joining("/"));
    }

    /**
     *  need the returning path to start and end with "/"
     */
    public String getPathForFileItem(String parentPath, String currentPath) {
        String path = OSUtil.normalizePath(parentPath, currentPath + "/");
        if (path.startsWith(getROOT_PATH()))
            return path;
        return getROOT_PATH() + path;
    }

    public FileSystemItem findByMId(String userId, long mId) {
        Query query = new Query(Criteria
                .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                .and(FileItemField.MEDIA_ID).is(mId)
        );
        return fileRepository.findOne(query);
    }

    public FileSystemItem getFileSystemItem(String userId, String id, boolean getCachedFirst) {
        FileSystemItem item = findById(userId, id, getCachedFirst);
        if (item == null)
            throw new IllegalArgumentException("File not found with id: " + id);
        return item;
    }

    public FileSystemItem findById(String userId, String id, boolean getCachedFirst) {
        if (id.equals(getROOT_FOLDER_ID()))
            return getRootDirectoryItem();
        return fileCacheService.getCachedFileElse(userId, id, getCachedFirst, fileId -> {
            Query query = new Query(Criteria
                    .where(FileItemField.USER_ID).is(Long.parseLong(userId))
                    .and("id").is(fileId)
            );
            return fileRepository.findOne(query);
        });
    }

    public List<FileSystemItem> getItemInIds(Collection<String> ids, boolean getCachedFirst, Criteria criteria, Predicate<FileSystemItem> filter) {
        if (getCachedFirst)
            return fileCacheService.getCachedFilesElse(ids, filter, keysToFetch -> {
                Query query = new Query(Criteria.where("id").in(keysToFetch));
                if (criteria != null) query.addCriteria(criteria);
                return fileRepository.find(query);
            });
        Query query = new Query(Criteria.where("id").in(ids));
        if (criteria != null) query.addCriteria(criteria);
        return fileRepository.find(query);
    }

    public static boolean containsInPath(String path, String target) {
        if (path == null || target == null || target.isEmpty()) {
            return false;
        }
        int targetLen = target.length();
        int pathLen = path.length();
        int idx = path.indexOf(target);
        while (idx != -1) {
            // Check boundary before the matched substring
            boolean validStart = (idx == 0 || path.charAt(idx - 1) == '/');

            // Check boundary after the matched substring
            int endIdx = idx + targetLen;
            boolean validEnd = (endIdx == pathLen || path.charAt(endIdx) == '/');

            if (validStart && validEnd) {
                return true;
            }
            // Move past the current occurrence to check subsequent matches
            idx = path.indexOf(target, idx + 1);
        }
        return false;
    }

    public FileSystemItem getRootDirectoryItem() {
        return fileCacheService.getFileCache(getROOT_FOLDER_ID(), (rootId) -> {
            Query query = new Query(Criteria
                    .where("id").is(rootId)
            );
            return fileRepository.findOne(query);
        });
    }

    public String getROOT_FOLDER_ID() {
        if (ROOT_FOLDER_ID != null) return ROOT_FOLDER_ID;
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(MEDIA_PATH)
                .and(FileItemField.PATH).is("/")
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );
        FileSystemItem item = fileRepository.findOne(query);

        if (item == null) throw new RuntimeException("Failed to find root folder");

        ROOT_FOLDER_ID = item.getId();
        return ROOT_FOLDER_ID;
    }

    private String getROOT_PATH() {
        if (ROOT_PATH != null) return ROOT_PATH;
        ROOT_PATH = "/" + getROOT_FOLDER_ID() + "/";
        return ROOT_PATH;
    }

}
