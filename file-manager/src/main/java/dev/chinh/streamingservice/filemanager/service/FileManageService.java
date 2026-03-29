package dev.chinh.streamingservice.filemanager.service;

import dev.chinh.streamingservice.filemanager.data.FileItemField;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileManageService {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createAtlasSearchIndex() {

        // 'lucene.standard' automatically lowercases text
        Document nameFieldDefinition = new Document("type", "string")
                .append("analyzer", "lucene.standard");

        // dynamic: false ensures we only spend RAM/Disk indexing the specific field we want
        Document indexDefinition = new Document("mappings",
                new Document("dynamic", false)
                        .append("fields", new Document(FileItemField.NAME, nameFieldDefinition))
        );

        String indexName = "fileNameSearchIndex";
        mongoTemplate.getCollection("fs_metadata").createSearchIndex(indexName, indexDefinition);
    }
}
