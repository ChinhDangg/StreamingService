package dev.chinh.streamingservice.filemanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.repository.FileSystemRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileFindService {

    private final FileSystemRepository fileSystemRepository;
    private final FileService fileService;
    private final ThumbnailService thumbnailService;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;

    private final Limit searchResultLimit = Limit.of(25);
    public record FileSearchResult(String parentId, String parentName, List<FileSystemItem> content, String nextCursor, boolean hasNext) {}

    public FileSearchResult findFilesAtRoot(String userId, String cursorStr, SortBy sortBy, Sort.Direction sortOrder) {
        Window<FileSystemItem> window = fileSystemRepository.findByUserIdAndParentId(
                Long.parseLong(userId),
                fileService.getROOT_FOLDER_ID(),
                getSort(sortBy, sortOrder),
                searchResultLimit,
                getFindingCursor(cursorStr)
        );

        List<FileSystemItem> itemInRoot = getUpdatedThumbnailUrl(userId, window.getContent());

        return new FileSearchResult(
                fileService.getROOT_FOLDER_ID(),
                FileService.MEDIA_PATH,
                itemInRoot,
                encodeCursor(getNextCursor(window)),
                window.hasNext());
    }

    public FileSearchResult findFilesInDirectory(String userId, boolean getFullPathInfo, String parentId, String cursorStr, SortBy sortBy, Sort.Direction sortOrder) {
        Window<FileSystemItem> window = fileSystemRepository.findByUserIdAndParentId(
                Long.parseLong(userId),
                parentId,
                getSort(sortBy, sortOrder),
                searchResultLimit,
                getFindingCursor(cursorStr)
        );
        List<FileSystemItem> itemInDir = getUpdatedThumbnailUrl(userId, window.getContent());

        if (getFullPathInfo) {
            FileSystemItem parentCraft = new FileSystemItem();
            String pathInId;
            if (itemInDir.isEmpty()) {
                FileSystemItem parent = fileService.getFileSystemItem(userId, parentId, true);
                pathInId = parent.getPath() + parent.getId() + "/";
                parentCraft.setName(parent.getName());
            } else {
                pathInId = itemInDir.getFirst().getPath();
                parentCraft.setName("unknown");
            }
            parentCraft.setPath(pathInId);
            String pathInName = fileService.getFullPathInName(parentCraft, false);
            return new FileSearchResult(pathInId, pathInName, itemInDir, encodeCursor(getNextCursor(window)), window.hasNext());
        }

        return new FileSearchResult(itemInDir.isEmpty() ? null : itemInDir.getFirst().getParentId(), null, itemInDir, encodeCursor(getNextCursor(window)), window.hasNext());
    }

    public FileSearchResult findFilesInDirectory(String userId, String parentId, String cursorStr, SortBy sortBy, Sort.Direction sortOrder) {
        FileSystemItem parent = fileService.getFileSystemItem(userId, parentId, true);
        String regexPath = "^" + Pattern.quote(parent.getPath() + parent.getId() + "/");

        Window<FileSystemItem> window = fileSystemRepository.findByUserIdAndPathRegex(
                Long.parseLong(userId),
                regexPath,
                getSort(sortBy, sortOrder),
                searchResultLimit,
                getFindingCursor(cursorStr)
        );

        return new FileSearchResult(null, null, window.getContent(), encodeCursor(getNextCursor(window)), window.hasNext());
    }

    public FileSearchResult searchFileByName(String userId, String parentId, String fileName, boolean isRecursive, String searchAfterToken) {
        FileSystemItem parent = fileService.getFileSystemItem(userId, parentId, true);

        List<AggregationOperation> stages = new ArrayList<>();
        String indexName = "fileNameSearchIndex";

        // Split words and enforce AND matching ---
        List<Document> mustClauses = new ArrayList<>();

        // This ensures hyphens, underscores, and symbols trigger the AND logic
        String[] searchWords = fileName.trim().split("[^\\p{L}\\p{N}]+");

        for (String word : searchWords) {
            if (!word.isBlank()) {
                mustClauses.add(new Document("autocomplete",
                        new Document("query", word)
                                .append("path", FileItemField.NAME)));
            }
        }

        List<Document> filterClauses = new ArrayList<>();
        if (isRecursive) {
            // Atlas Search 'regex' operator
            String pathPrefix = parent.getPath() + parent.getId() + "/.*";
            filterClauses.add(new Document("regex",
                    new Document("query", pathPrefix)
                            .append("path", FileItemField.PATH)
            ));
        } else {
            // Atlas Search 'equals' operator for exact parent_id match
            filterClauses.add(new Document("text",
                    new Document("query", parent.getId())
                            .append("path", FileItemField.PARENT_ID)
            ));
        }

        Document compoundDoc = new Document("must", mustClauses)
                .append("filter", filterClauses);

        // Wrap the clauses in a compound "must" (which acts as an AND operator)
        Document searchStageOoc = new Document("index", indexName)
                .append("compound", compoundDoc);

        // Inject the cursor token if this is page 2+
        if (searchAfterToken != null && !searchAfterToken.isBlank()) {
            searchStageOoc.append("searchAfter", searchAfterToken);
        }

        Document searchDoc = new Document("$search", searchStageOoc);
        stages.add(context -> searchDoc);

        final int size = 25;
        stages.add(Aggregation.limit(size));

        // Project the pagination token out of the Lucene metadata
        stages.add(context -> new Document("$project", new Document("document", "$$ROOT")
                .append("pageToken", new Document("$meta", "searchSequenceToken"))
        ));

        // Replace the root so the mapped object maps cleanly, while keeping the token
        stages.add(Aggregation.replaceRoot().withValueOf(
                context -> new Document("$mergeObjects", Arrays.asList("$document", new Document("pageToken", "$pageToken")))
        ));

        Aggregation aggregation = Aggregation.newAggregation(stages);
        List<FileSystemItem> results = mongoTemplate.aggregate(aggregation, "fs_metadata", FileSystemItem.class).getMappedResults();
        getUpdatedThumbnailUrl(userId, results);

        String nextToken = (results.size() == size) ? results.getLast().getPageToken() : null;
        return new FileSearchResult(null, null, results, nextToken, nextToken != null);
    }


    private ScrollPosition getFindingCursor(String cursorStr) {
        ScrollPosition scrollPosition = decodeCursor(cursorStr);
        return (scrollPosition != null) ? scrollPosition : ScrollPosition.keyset();
    }

    private ScrollPosition getNextCursor(Window<FileSystemItem> window) {
        return window.hasNext()
                ? window.positionAt(window.getContent().size() - 1)
                : null;
    }

    private List<FileSystemItem> getUpdatedThumbnailUrl(String userId, List<FileSystemItem> source) {
        List<String> thumbnailName = thumbnailService.processThumbnail(userId, source);
        for (int i = 0; i < thumbnailName.size(); i++) {
            source.get(i).setThumbnail(thumbnailName.get(i));
        }
        return source;
    }

    private Sort getSort(SortBy sortBy, Sort.Direction sortOrder) {
        Sort sort = Sort.by(sortOrder, sortBy.getField());

        if (sortBy == SortBy.RESOLUTION) {
            // Automatically add the width tie-breaker
            sort = sort.and(Sort.by(sortOrder, ContentMetaData.RESOLUTION + "." + ContentMetaData.WIDTH));
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return sort;
    }


    // Convert from Frontend String to Spring ScrollPosition
    public ScrollPosition decodeCursor(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return ScrollPosition.keyset(); // Starts at the beginning
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor);
            Map<String, Object> keys = objectMapper.readValue(decodedBytes, new TypeReference<>() {});
            return ScrollPosition.forward(keys);
        } catch (Exception e) {
            // If the cursor is malformed, throw a 400 Bad Request
            throw new IllegalArgumentException("Invalid cursor format", e);
        }
    }

    // Convert from Spring ScrollPosition to Frontend String
    public String encodeCursor(ScrollPosition position) {
        if (position == null || position.isInitial()) {
            return null;
        }
        try {
            KeysetScrollPosition keyset = (KeysetScrollPosition) position;
            String json = objectMapper.writeValueAsString(keyset.getKeys());
            return Base64.getUrlEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode cursor", e);
        }
    }
}
