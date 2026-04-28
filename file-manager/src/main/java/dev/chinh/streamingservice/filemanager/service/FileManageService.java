package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.MongoCommandException;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileManageService {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createAtlasSearchIndex() {

        // Create a CUSTOM analyzer that NEVER splits words or drops punctuation
        // It takes the exact string and just lowercases it.
        Document customAnalyzer = new Document("name", "filename_analyzer")
                .append("tokenizer", new Document("type", "keyword"))
                .append("tokenFilters", List.of(new Document("type", "lowercase")));

        // Assign our custom analyzer to the name field
        Document nameFieldDefinition = new Document("type", "string")
                .append("analyzer", "filename_analyzer");

        // dynamic: false ensures we only spend RAM/Disk indexing the specific field we want
        Document mappings = new Document("dynamic", false)
                .append("fields", new Document(FileItemField.NAME, nameFieldDefinition));

        // Add the custom analyzer definition to the index alongside the mappings
        Document indexDefinition = new Document("mappings", mappings)
                .append("analyzers", Collections.singletonList(customAnalyzer));

        String indexName = "fileNameSearchIndex";

        try {
            mongoTemplate.getCollection("fs_metadata").createSearchIndex(indexName, indexDefinition);
            System.out.println("Successfully created Atlas Search index: " + indexName);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 68) {
                System.out.println("Atlas Search index definition changed. Updating index: " + indexName);
                mongoTemplate.getCollection("fs_metadata").updateSearchIndex(indexName, indexDefinition);
            } else {
                throw e;
            }
        }
    }
}
