package dev.chinh.streamingservice.searchclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import dev.chinh.streamingservice.searchclient.data.MediaSearchRangeField;
import dev.chinh.streamingservice.searchclient.data.SearchFieldGroup;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.analysis.*;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper;

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

    public <T> SearchResponse<T> searchContaining(String index, String field, String text, Class<T> clazz) throws IOException {
        return client.search(s -> s
                        .index(index)
                        .query(q -> q
                                .match(m -> m
                                        .field(field)
                                        .query(FieldValue.of(text))
                                )
                        ),
                clazz
        );
    }

    public SearchResponse<Object> advanceSearch(String index,
                                                List<SearchFieldGroup> includeGroups,
                                                List<SearchFieldGroup> excludeGroups,
                                                List<MediaSearchRangeField> mediaSearchRanges,
                                                int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder rootBool = new BoolQuery.Builder();

        if (mediaSearchRanges != null) {
            for (MediaSearchRangeField mediaSearchRange : mediaSearchRanges) {
                RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
                rangeBuilder.field(mediaSearchRange.getField());
                if (mediaSearchRange.getFrom() != null)
                    rangeBuilder.gte(JsonData.of(mediaSearchRange.getFrom()));
                if (mediaSearchRange.getTo() != null)
                    rangeBuilder.lte(JsonData.of(mediaSearchRange.getTo()));
                rootBool.filter(Query.of(q -> q.range(rangeBuilder.build())));
            }
        }

        if (includeGroups != null) {
            for (SearchFieldGroup group : includeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                BoolQuery.Builder groupBool = new BoolQuery.Builder();

                if (group.isMatchAll()) {
                    for (Object value : values) {
                        groupBool.must(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                } else {
                    for (Object value : values) {
                        groupBool.should(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                    groupBool.minimumShouldMatch("1");
                }
                rootBool.must(Query.of(q -> q.bool(groupBool.build())));
            }
        }

        if (excludeGroups != null) {
            for (SearchFieldGroup group : excludeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                for (Object value : values) {
                    rootBool.mustNot(buildTermOrMatch(field, value, group.isSearchTerm()));
                }
            }
        }

        Query rootQuery = Query.of(q -> q.bool(rootBool.build()));

        return searchWithQuery(index, rootQuery, page, size, sortBy, sortOrder);
    }

    private Query buildTermOrMatch(String field, Object value, boolean searchTerm) {
        if (searchTerm) {
            // Term Query
            return Query.of(q -> q
                    .term(t -> t
                            .field(field)
                            .value(FieldValue.of(value.toString()))
                    )
            );
        } else {
            // Match Query
            return Query.of(q -> q
                    .match(m -> m
                            .field(field)
                            .query(FieldValue.of(value.toString()))
                    )
            );
        }
    }

    public SearchResponse<Object> search(String index, Object text, int page, int size, SortBy sortBy,
                                    SortOrder sortOrder) throws IOException {
        Query multiMatch = Query.of(q -> q
                .multiMatch(m -> m
                        .query(text.toString())
                        .fields(
                                ContentMetaData.TITLE + "^3.0",
                                ContentMetaData.UNIVERSES + ".search^2.0",
                                ContentMetaData.CHARACTERS + ".search^2.0",
                                ContentMetaData.TAGS + ".search^1.5",
                                ContentMetaData.AUTHORS + ".search^1.0"
                        )
                        .prefixLength(1)
                        .fuzziness("AUTO")
                        .type(TextQueryType.BestFields)
                )
        );
        return searchWithQuery(index, multiMatch, page, size, sortBy, sortOrder);
    }

    public SearchResponse<Object> searchMatchAll(String index, int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        Query matchAll = Query.of(q -> q
                .matchAll(m -> m)
        );
        return searchWithQuery(index, matchAll, page, size, sortBy, sortOrder);
    }

    /**
     * Search exactly with given search strings by field.
     * Does not work with fields that have text type. Use search match for that.
     */
    public SearchResponse<Object> searchTermByOneField(String index, String field, List<Object> text, boolean matchAll, int page, int size,
                                               SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder termBoolBuilder = new BoolQuery.Builder();
        if (matchAll) {
            for (Object term : text) {
                termBoolBuilder.must(buildTermOrMatch(field, term, true));
            }
        } else {
            for (Object term : text) {
                termBoolBuilder.should(buildTermOrMatch(field, term, true));
            }
            termBoolBuilder.minimumShouldMatch("1");
        }
        Query termBoolQuery = Query.of(q -> q.bool(termBoolBuilder.build()));
        return searchWithQuery(index, termBoolQuery, page, size, sortBy, sortOrder);
    }

    private SearchResponse<Object> searchWithQuery(String index, Query query, int page, int size, SortBy sortBy,
                                                   SortOrder sortOrder) throws IOException {

        String sortByField = switch (sortBy) {
            case SortBy.UPLOAD_DATE -> ContentMetaData.UPLOAD_DATE;
            case SortBy.LENGTH -> ContentMetaData.LENGTH;
            case SortBy.SIZE -> ContentMetaData.SIZE;
            case SortBy.YEAR -> ContentMetaData.YEAR;
            default -> null;
        };

        SortOptions scoreTieBreaker = SortOptions.of(o -> o.score(s -> s.order(sortOrder)));

        SortOptions primarySort = (sortByField == null)
                ? scoreTieBreaker
                : SortOptions.of(o -> o.field(f -> f.field(sortByField).order(sortOrder)));

        SearchResponse<Object> response = client.search(s -> s
                .index(index)
                .from(page * size)
                .size(size)
                .query(query)
                .sort(primarySort, scoreTieBreaker)
                .trackTotalHits(t -> t.count(1000)), Object.class
        );

        return response;
    }
}