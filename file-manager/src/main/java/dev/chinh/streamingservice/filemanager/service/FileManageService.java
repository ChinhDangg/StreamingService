package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.MongoCommandException;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileManageService {

    private static final Logger log = LoggerFactory.getLogger(FileManageService.class);
    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createAtlasSearchIndex() {
        // Tokenizer: Split on ANYTHING that is NOT a Unicode Letter or Number.
        // (This automatically handles spaces, hyphens, and underscores.)
        Document splitTokenizer = new Document("type", "regexSplit")
                .append("pattern", "[^\\p{L}\\p{N}]+");

        // Define ONE Custom Analyzer (Tokenize & Lowercase)
        Document filenameAnalyzer = new Document("name", "filename_analyzer")
                .append("tokenizer", splitTokenizer)
                .append("tokenFilters", List.of(
                        new Document("type", "lowercase")
                ));

        // Let Atlas natively handle the edge-gramming via the autocomplete type
        Document nameFieldDefinition = new Document("type", "autocomplete")
                .append("analyzer", "filename_analyzer")
                .append("tokenization", "edgeGram")
                .append("minGrams", 1) // plural 'minGrams' for field definitions
                .append("maxGrams", 15);

        // add path and parent_id for matching in search first before searching (filter unwanted first rather than search in all first)
        Document pathFieldDefinition = new Document("type", "string")
                .append("analyzer", "lucene.keyword"); // match on exact path
        Document parentIdFieldDefinition = new Document("type", "string")
                .append("analyzer", "lucene.keyword"); // match on exact parent_id

        Document fields = new Document(FileItemField.NAME, nameFieldDefinition)
                .append(FileItemField.PATH, pathFieldDefinition)
                .append(FileItemField.PARENT_ID, parentIdFieldDefinition);

        // Final Index Mappings & Definition
        Document mappings = new Document("dynamic", false)
                .append("fields", fields);

        Document indexDefinition = new Document("mappings", mappings)
                .append("analyzers", List.of(filenameAnalyzer));

        // max objects in a search index are 2.1 billion
        String indexName = "fileNameSearchIndex";

        try {
            mongoTemplate.getCollection("fs_metadata").createSearchIndex(indexName, indexDefinition);
            log.info("Successfully created Atlas Search index: {}", indexName);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 68) {
                log.info("Atlas Search index definition changed. Updating index: {}", indexName);
                mongoTemplate.getCollection("fs_metadata").updateSearchIndex(indexName, indexDefinition);
            } else {
                throw e;
            }
        }
    }
}
