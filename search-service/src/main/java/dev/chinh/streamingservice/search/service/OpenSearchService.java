package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.analysis.*;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
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
                        String version1 = constant.getName() + "_v1";
                        createIndexWithSettingAndMapping(version1, "/mapping/media-name-entity-mapping.json");
                        addAliasToIndex(version1, constant.getName());
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
     * Property nameProperty = Property.of(p -> p
     *                     .text(t -> t
     *                             .analyzer("autocomplete")
     *                             .searchAnalyzer("autocomplete_search")
     *                             .fields("raw", f -> f.keyword(k -> k))
     *                     )
     *             );
     *
     *             Property idProperty = Property.of(p -> p.long_(l -> l));
     *
     *             Property tagsProperty = Property.of(p -> p
     *                     .nested(n -> n
     *                             .properties("id", idProperty)
     *                             .properties("name", nameProperty)
     *                     )
     *             );
     */
    public void updateIndexMapping(String indexName, Map<String, Property> properties) throws IOException {
        PutMappingResponse response = client.indices().putMapping(p -> p
                .index(indexName)
                .properties(properties)
        );
        System.out.println("Mapping updated? " + response.acknowledged());
    }

    public void updateIndexSettings(String indexName) throws IOException {
        PutIndicesSettingsRequest request = PutIndicesSettingsRequest.builder()
                .index(indexName)
                .settings(s -> s
                        .analysis(a -> a
                                // 1. Define a custom tokenizer for your edge_ngram settings
                                .tokenizer("custom_edge_ngram", t -> t
                                        .definition(td -> td
                                                .edgeNgram(e -> e
                                                        .minGram(2)
                                                        .maxGram(20)
                                                        .tokenChars(List.of(TokenChar.Letter, TokenChar.Digit,  TokenChar.Punctuation, TokenChar.Symbol))
                                                )
                                        )
                                )
                                // 2. Reference that custom tokenizer inside the analyzer
                                .analyzer("autocomplete_search", an -> an
                                        .custom(c -> c
                                                .tokenizer("custom_edge_ngram")
                                                .filter("lowercase")
                                        )
                                )
                        )
                )
                .build();

        client.indices().close(c -> c.index(indexName));
        try {
            PutIndicesSettingsResponse response = client.indices().putSettings(request);
            if (response.acknowledged()) {
                System.out.println("Settings updated successfully.");
            } else {
                System.out.println("Settings update failed.");
            }
        } catch (Exception e) {
            System.out.println("Settings update failed with error: " + e.getMessage());
            e.printStackTrace();
        }
        client.indices().open(o -> o.index(indexName));
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
                                                      String nestedSubFieldName,
                                                      String newName) throws IOException {
        String painlessScript =
            "if (ctx._source.containsKey(params.fieldName) && ctx._source[params.fieldName] instanceof List) { " +
            "    for (item in ctx._source[params.fieldName]) { " +
            "        if (item != null && item.id == params.nestedId) { " +
            "            item[params.nestedSubFieldName] = params.newName; " +
            "        } " +
            "    } " +
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
                                        "fieldName", JsonData.of(fieldName),
                                        "nestedId", JsonData.of(nestedId),
                                        "nestedSubFieldName", JsonData.of(nestedSubFieldName),
                                        "newName", JsonData.of(newName)
                                ))
                        )
                )
                .waitForCompletion(false)
                .refresh(Refresh.False) // default anyway
                .build();

        UpdateByQueryResponse response = client.updateByQuery(request);
        System.out.println("Updated " + response.updated() + " documents with new name");
    }

    /**
     * Updates the length field for documents matching the given IDs.
     *
     * @param indexName      Target index name
     * @param docIds         List of custom document IDs
     * @param delta          Amount to adjust length by (e.g., +1 to increase, -1 to decrease)
     * @param idempotencyKey Key used to prevent duplicate executions
     */
    public void updateNumericFieldWithDeltaByIds(
            String indexName,
            List<Long> docIds,
            String numericFieldName,
            int delta,
            String idempotencyKey) throws IOException {

        // Define script source checking idempotency key before modifying length
        String scriptSource =
                "if (ctx._source.last_idempotency_key != params.key) { " +
                "    if (ctx._source[params.field] != null) { " +
                "        ctx._source[params.field] += params.delta; " +
                "    } " +
                "    ctx._source.last_idempotency_key = params.key; " +
                "} else { " +
                "    ctx.op = 'noop'; " + // Skip operation and avoid incrementing version
                "}";

        // Build Painless script with parameters
        Script updateScript = Script.of(s -> s
                .inline(InlineScript.of(i -> i
                        .lang(l -> l.builtin(BuiltinScriptLanguage.Painless))
                        .source(scriptSource)
                        .params(Map.of(
                                "field", JsonData.of(numericFieldName),
                                "delta", JsonData.of(delta),
                                "key", JsonData.of(idempotencyKey)
                        ))
                ))
        );

        // 3. Query custom field 'id'
        List<FieldValue> fieldValues = docIds.stream()
                .map(FieldValue::of)
                .toList();

        Query customIdQuery = TermsQuery.of(t -> t
                .field("id") // Use "id.keyword" if mapped as text + keyword
                .terms(TermsQueryField.of(tf -> tf.value(fieldValues)))
        ).toQuery();

        UpdateByQueryRequest request = UpdateByQueryRequest.of(u -> u
                .index(indexName)
                .query(customIdQuery)
                .script(updateScript)
                .conflicts(Conflicts.Proceed)
        );

        UpdateByQueryResponse response = client.updateByQuery(request);

        if (response.versionConflicts() != null && response.versionConflicts() > 0) {
            // Log or metric trigger: Some documents were skipped due to concurrent writes.
            // Re-issuing the method call with the same idempotencyKey will safely complete them!
            System.err.println("Encountered " + response.versionConflicts() + " version conflicts.");
        }

        System.out.println("Updated documents: " + response.updated());
        System.out.println("No-op (skipped) documents: " + response.noops());
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

    public void updateDocumentsByQuery(String indexName, List<Long> ids, Map<String, Object> updateFields, long version) throws IOException {
        List<String> stringIds = ids.stream()
                .map(String::valueOf)
                .toList();

        Map<String, Object> updateFieldsWithVersion = new HashMap<>(updateFields);
        updateFieldsWithVersion.put("version", version);

        Script script = Script.of(s -> s
                .inline(InlineScript.of(i -> i
                        .source("for (entry in params.fields.entrySet()) { ctx._source[entry.getKey()] = entry.getValue(); }")
                        .params("fields", JsonData.of(updateFieldsWithVersion))
                ))
        );

        UpdateByQueryRequest request = UpdateByQueryRequest.of(u -> u
                .index(indexName)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.ids(idsQuery -> idsQuery.values(stringIds)))
                        // ONLY update if the document is missing db_version OR has an older version
                        .filter(f -> f.bool(inner -> inner
                                .should(s -> s.bool(noVer -> noVer.mustNot(m -> m.exists(e -> e.field("version")))))
                                .should(s -> s.range(r -> r.field("version").lt(JsonData.of(version))))
                                .minimumShouldMatch("1")
                        ))
                ))
                .script(script)
                .conflicts(Conflicts.Proceed)
                .refresh(Refresh.False)
        );

        UpdateByQueryResponse response = client.updateByQuery(request);

        System.out.printf("Total updated: %d, Version conflicts: %d%n",
                response.updated(),
                response.versionConflicts());
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
