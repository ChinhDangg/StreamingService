package dev.chinh.streamingservice.searchindexer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.BuiltinScriptLanguage;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.analysis.*;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper;

    public static final String MEDIA_INDEX_NAME = "media";


    public void _initializeIndexes() throws InterruptedException {
        int retryCount = 20;
        while (retryCount-- > 0) {
            try {
                if (!indexExists(MEDIA_INDEX_NAME)) {
                    String version1 = MEDIA_INDEX_NAME + "_v1";
                    createIndexWithSettingAndMapping(version1, "/mapping/media-mapping.json");
                    addAliasToIndex(version1, MEDIA_INDEX_NAME);
                }
                for (MediaNameEntityConstant constant : MediaNameEntityConstant.values()) {
                    if (!indexExists(constant.getName())) {
                        createIndexWithSettingAndMapping(constant.getName(), "/mapping/media-name-entity-mapping.json");
                    }
                }
                break;
            } catch (IOException e) {
                if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Connection closed")) {
                    System.out.println("Retrying opensearch connection: " + retryCount);
                    Thread.sleep(500);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void createIndexWithSettingAndMapping(String indexName, String mappingPath) throws IOException {
        Map<String, Object> map;

        ClassPathResource resource = new ClassPathResource(mappingPath);
        try (InputStream is = resource.getInputStream()) {
            map = objectMapper.readValue(is.readAllBytes(), new TypeReference<>() {});
        }

        JsonpMapper jsonpMapper = client._transport().jsonpMapper();

        Object settings = map.get("settings");
        IndexSettings indexSettings = JsonData.of(settings).to(IndexSettings.class, jsonpMapper);

        Object mappings = map.get("mappings");
        TypeMapping typeMapping = JsonData.of(mappings).to(TypeMapping.class, jsonpMapper);

        CreateIndexRequest req = new CreateIndexRequest.Builder()
                .index(indexName)
                .settings(indexSettings)
                .mappings(typeMapping)
                .build();

        CreateIndexResponse resp = client.indices().create(req);

        System.out.println("Index created: " + resp.index());
    }

    // check setting: http://localhost:9200/{name-entity}/_settings
    public void createIndexForNameEntity(String indexName) throws IOException {

        String autocomplete_tokenizer = "autocomplete_tokenizer";
        String autocomplete_analyzer = "autocomplete_analyzer";
        String autocomplete_search_analyzer = "autocomplete_search_analyzer";

        EdgeNGramTokenizer edgeNGramTokenizer = EdgeNGramTokenizer.builder()
                .minGram(2)
                .maxGram(20)
                .tokenChars(List.of(TokenChar.Letter, TokenChar.Digit))
                .build();

        CustomAnalyzer customAutoCompleteAnalyzer = CustomAnalyzer.builder()
                .tokenizer(autocomplete_tokenizer)
                .filter(List.of("lowercase"))
                .build();

        CustomAnalyzer customAutoCompleteSearchAnalyzer = CustomAnalyzer.builder()
                .tokenizer("lowercase")
                .build();

        IndexSettingsAnalysis analysis = IndexSettingsAnalysis.builder()
                .tokenizer(autocomplete_tokenizer, Tokenizer.builder()
                        .definition(edgeNGramTokenizer.toTokenizerDefinition())
                        .build())
                .analyzer(Map.of(
                        autocomplete_analyzer, Analyzer.builder()
                                .custom(customAutoCompleteAnalyzer)
                                .build(),
                        autocomplete_search_analyzer, Analyzer.builder()
                                .custom(customAutoCompleteSearchAnalyzer)
                                .build()
                ))
                .build();

        IndexSettings settings = IndexSettings.builder()
                .analysis(analysis)
                .build();

        TypeMapping mapping = TypeMapping.builder()
                .properties(Map.of(
                        "name", Property.of(m -> m.text(t -> t.analyzer("english")))
                ))
                .build();

        // 5. Construct the final CreateIndexRequest
        CreateIndexRequest req = new CreateIndexRequest.Builder()
                .index(indexName)
                .settings(settings)
                .mappings(mapping)
                .build();

        CreateIndexResponse resp = client.indices().create(req);

        System.out.println("Index created: " + resp.index());
    }

    // http://localhost:9200/media
    public void createIndexWithMapping(String indexName, String mappingPath) throws IOException {
        String mappingJson = loadMapping(mappingPath);

        CreateIndexResponse createIndexResponse = client.indices().create(c -> c
                .index(indexName)
                .mappings(m -> m.withJson(new StringReader(mappingJson)))
        );

        System.out.println("Index created: " + createIndexResponse.index());
    }

    private String loadMapping(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
        return client.indices().exists(existsRequest).value();
    }

    /**
     * Example:
     * Map<String, Property> newProperties = Map.of(
     *     "description", Property.of(m -> m.text(t -> t.analyzer("english"))),
     *     "sku",         Property.of(m -> m.keyword(k -> k))
     * );
     */
    public void updateIndexMapping(String indexName, Map<String, Property> properties) throws IOException {
        PutMappingResponse response = client.indices().putMapping(p -> p
                .index(indexName)
                .properties(properties)
        );
        System.out.println("Mapping updated? " + response.acknowledged());
    }

    public void deleteIndex(String indexName) throws IOException {
        DeleteIndexResponse response = client.indices().delete(d -> d.index(indexName));
        System.out.println("Index deleted: " + response.acknowledged());
    }

    public void reindex(String sourceIndex, String targetIndex) throws IOException {
        ReindexResponse reindexResponse = client.reindex(r -> r
                .source(s -> s.index(sourceIndex))
                .dest(d -> d.index(targetIndex))
        );
        System.out.println("Reindex completed.");
        System.out.println("Documents created: " + reindexResponse.created());
        System.out.println("Documents updated: " + reindexResponse.updated());
        System.out.println("Time taken (millis): " + reindexResponse.took());
    }

    public void verifyIndexCount(String indexName1, String indexName2) throws IOException {
        CountResponse countResponse1 = client.count(c -> c.index(indexName1));
        System.out.println(indexName1 + " index count = " + countResponse1.count());
        if (indexName2 != null) {
            CountResponse countResponse2 = client.count(c -> c.index(indexName2));
            System.out.println(indexName2 + " index count = " + countResponse2.count());
        }
    }

    public void addAliasToIndex(String indexName, String alias) throws IOException {
        UpdateAliasesResponse response = client.indices().updateAliases(a -> a
                .actions(act -> act
                        .add(addAlias -> addAlias
                                .index(indexName)
                                .alias(alias)
                        )
                )
        );
        if (response.acknowledged()) {
            System.out.println("Successfully added alias '" + alias + "' to index '" + indexName + "'.");
        } else {
            System.err.println("Failed to acknowledge alias creation for index '" + indexName + "'.");
        }
    }

    public void removeAliasFromIndex(String indexName, String alias) throws IOException {
        UpdateAliasesResponse response = client.indices().updateAliases(a -> a
                .actions(act -> act
                        .remove(removeAlias -> removeAlias
                                .index(indexName)
                                .alias(alias)
                        )
                )
        );
        if (response.acknowledged()) {
            System.out.println("Successfully removed alias '" + alias + "' from index '" + indexName + "'.");
        } else {
            System.err.println("Failed to acknowledge alias removal for index '" + indexName + "'.");
        }
    }

    public void refreshExistingData(String indexName) throws IOException {
        UpdateByQueryRequest request = UpdateByQueryRequest.of(u -> u
                .index(indexName)
                .refresh(Refresh.True)
        );
        client.updateByQuery(request);
        System.out.println("Refreshed existing data in index: " + indexName);
    }

    public void deleteDocument(String indexName, long id) throws IOException {
        DeleteResponse response = client.delete(d -> d
                .index(indexName)
                .id(String.valueOf(id))
        );
        System.out.println("Deleted doc id=" + id + " result=" + response.result());
    }

    // http://localhost:9200/media/_doc/1?pretty
    /**
     * Add a new doc to the index with given id.
     * Will only pass if the id doesn't exist yet.
     */
    public void indexDocument(String indexName, long id, Map<String, Object> doc) throws IOException {
        IndexResponse response = client.index(i -> i
                .index(indexName)
                .id(String.valueOf(id))
                .document(doc)
                .opType(OpType.Create)
        );
        System.out.println("Indexed doc with id: " + response.id());
    }

    public <TDocument> void indexDocument(String indexName, long id, TDocument searchItem) throws IOException {
        IndexRequest<TDocument> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(String.valueOf(id))
                .document(searchItem)
        );
        IndexResponse response = client.index(request);
        System.out.println("Indexed class doc with id: " + response.id());
    }

    public void updateAllNestedFieldNameWithIdInIndex(String indexName,
                                                      String fieldName,
                                                      long nestedId,
                                                      String nestFieldName,
                                                      String newName) throws IOException {
        String painlessScript =
        "if (ctx._source." + fieldName + " != null) { " +
        "   for (def item : ctx._source." + fieldName + ") { " +
        "       if (item.id == params.nestedId) { " +
        "           item." + nestFieldName + " = params.newName; " +
        "       } " +
        "   } " +
        "}";

        UpdateByQueryRequest request = new UpdateByQueryRequest.Builder()
                .index(indexName)
                .query(q -> q
                        .nested(n -> n
                                .path(fieldName)
                                .query(nq -> nq
                                        .term(t -> t
                                                .field(fieldName + ".id")
                                                .value(v -> v.longValue(nestedId))
                                        )
                                )
                        )
                )
                .script(s -> s
                        .inline(i -> i
                                .lang(l -> l.builtin(BuiltinScriptLanguage.Painless))
                                .source(painlessScript)
                                .params(Map.of(
                                        "nestedId", JsonData.of(nestedId),
                                        "newName", JsonData.of(newName)
                                ))
                        )
                )
                .refresh(Refresh.True)
                .build();

        UpdateByQueryResponse response = client.updateByQuery(request);
        System.out.println("Updated " + response.updated() + " documents with new name");
    }

    /**
     * Will replace all existing fields with new values.
     * Will add new fields if doesn't exist previously.
     * @param updateFields String-name of the field; Object-values
     */
    public void partialUpdateDocument(String indexName, long id, Map<String, Object> updateFields) throws IOException {
        UpdateResponse<Object> response = client.update(u -> u
                .index(indexName)
                .id(String.valueOf(id))
                .doc(updateFields), Object.class
        );
        System.out.println("Document updated, result: " + response.result());
    }

    /**
     * Adding new values to one existing field for given document id.
     * @param id the id of the doc.
     */
    public void appendValueToFieldInDocument(String indexName, long id, String field, Object values) throws IOException {
        Map<String, JsonData> params = Collections.singletonMap(field, values)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));

        Script inlineScript = Script.of(s -> s
                .inline(i -> i
                        .source("ctx._source." + field + ".add(params." + values + ")")
                        .lang(l -> l.builtin(BuiltinScriptLanguage.Painless))
                        .params(params)
                )
        );

        UpdateResponse<Object> response = client.update(u -> u
                .index(indexName)
                .id(String.valueOf(id))
                .script(inlineScript), Object.class
        );
        System.out.println("Scripted update done, result: " + response.result());
    }
}
