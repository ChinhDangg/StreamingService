package dev.chinh.streamingservice.search.service;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.search.constant.SortBy;
import dev.chinh.streamingservice.search.data.MediaSearchRangeField;
import dev.chinh.streamingservice.search.data.SearchFieldGroup;
import lombok.RequiredArgsConstructor;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.PutMappingRequest;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.*;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final RestHighLevelClient client;

    // http://localhost:9200/media
    public void createIndexWithMapping() throws IOException {
        String mappingJson = loadMapping("mapping/media-mapping.json");

        CreateIndexRequest request = new CreateIndexRequest("media");
        request.mapping(mappingJson, XContentType.JSON);

        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

        System.out.println("Index created: " + createIndexResponse.index());
    }

    private String loadMapping(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void updateMapping(String fieldName, String fieldType) throws IOException {

        // Build JSON mapping programmatically
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("properties");
            {
                builder.startObject(fieldName);
                builder.field("type", fieldType);
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();

        PutMappingRequest request = new PutMappingRequest("media");
        request.source(builder);

        AcknowledgedResponse response = client.indices().putMapping(request, RequestOptions.DEFAULT);
        System.out.println("Mapping updated? " + response.isAcknowledged());
    }

    public void deleteDocument(long id) throws IOException {
        DeleteRequest request = new DeleteRequest("media", String.valueOf(id));
        DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
        System.out.println("Deleted doc id=" + id + " result=" + response.getResult());
    }

    // http://localhost:9200/media/_doc/1?pretty

    /**
     * Add a new doc to the index with given id.
     * Will only pass if the id doesn't exist yet.
     */
    public void indexDocument(long id, Map<String, Object> doc) throws IOException {
        IndexRequest request = new IndexRequest("media")
                .id(String.valueOf(id))
                .source(doc)
                .opType(DocWriteRequest.OpType.CREATE); // give error if an id already exist

        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        System.out.println("Indexed with id: " + response.getId());
    }

    /**
     * Will replace all existing fields with new values.
     * Will add new fields if doesn't exist previously.
     * @param updateFields String-name of the field; Object-values
     */
    public void partialUpdateDocument(long id, Map<String, Object> updateFields) throws IOException {
        UpdateRequest request = new UpdateRequest("media", String.valueOf(id))
                .doc(updateFields);

        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Document updated, result: " + response.getResult());
    }

    /**
     * Adding new values to one existing field for given document id.
     * @param id the id of the doc.
     */
    public void appendValueToFieldInDocument(long id, String field, Object values) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put(field, values);

        UpdateRequest request = new UpdateRequest("media", String.valueOf(id))
                .script(new Script(ScriptType.INLINE,
                        "painless",
                        "ctx._source." + field + ".add(params." + field + ")",
                        params));

        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Scripted update done, result: " + response.getResult());
    }

    public SearchResponse advanceSearch(List<SearchFieldGroup> includeGroups, List<SearchFieldGroup> excludeGroups,
                                        List<MediaSearchRangeField> mediaSearchRanges,
                                        int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQueryBuilder rootQuery = QueryBuilders.boolQuery();

        // Handle range requests
        if (mediaSearchRanges != null) {
            for (MediaSearchRangeField mediaSearchRange : mediaSearchRanges) {
                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(mediaSearchRange.getField());
                if (mediaSearchRange.getFromValue() != null) rangeQuery.gte(mediaSearchRange.getFromValue());
                if (mediaSearchRange.getToValue() != null) rangeQuery.lte(mediaSearchRange.getToValue());
                rootQuery.must(rangeQuery);
            }
        }

        // Handle include groups
        if (includeGroups != null) {
            for (SearchFieldGroup group : includeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                BoolQueryBuilder groupQuery = QueryBuilders.boolQuery();

                if (group.isMustAll()) { // AND logic: must include all values
                    for (Object value : values) {
                        groupQuery.must(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                } else { // OR logic: match any
                    for (Object value : values) {
                        groupQuery.should(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                    groupQuery.minimumShouldMatch(1);
                }

                rootQuery.must(groupQuery); // each group combined with AND at root level
            }
        }

        // Handle exclude groups (no range)
        if (excludeGroups != null) {
            for (SearchFieldGroup group : excludeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                for (Object value : values) {
                    rootQuery.mustNot(buildTermOrMatch(field, value, group.isSearchTerm()));
                }
            }
        }

        return searchWithQueryBuilder(rootQuery, page, size, sortBy, sortOrder);
    }

    private QueryBuilder buildTermOrMatch(String field, Object value, boolean searchTerm) {
        if (searchTerm) {
            return QueryBuilders.termQuery(field, value);
        } else {
            return QueryBuilders.matchQuery(field, value);
        }
    }

    public SearchResponse search(Object text, int page, int size, SortBy sortBy,
                                    SortOrder sortOrder) throws IOException {
        QueryBuilder q = QueryBuilders
                .multiMatchQuery(text)
                .field("title", 3.0f)
                .field("universes", 2.0f)
                .field("characters", 2.0f)
                .field("tags", 1.5f)
                .field("authors", 1.0f)
                .prefixLength(1)
                .fuzziness(Fuzziness.AUTO)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);

        return searchWithQueryBuilder(q, page, size, sortBy, sortOrder);
    }

    public SearchResponse searchMatchByOneField(String field, Object text, int page, int size,
                                                SortBy sortBy, SortOrder sortOrder) throws IOException {
        QueryBuilder q = QueryBuilders.matchQuery(field, text);
        return searchWithQueryBuilder(q, page, size, sortBy, sortOrder);
    }

    /**
     * Search exactly with given search strings by field.
     * Does not work with fields that have text type. Use search match for that.
     */
    public SearchResponse searchTermByOneField(String field, Collection<Object> text, int page, int size,
                                               SortBy sortBy, SortOrder sortOrder) throws IOException {
        QueryBuilder q = QueryBuilders.termsQuery(field, text);
        return searchWithQueryBuilder(q, page, size, sortBy, sortOrder);
    }

    private SearchResponse searchWithQueryBuilder(QueryBuilder queryBuilder, int page, int size, SortBy sortBy,
                                               SortOrder sortOrder) throws IOException {

        SearchRequest searchRequest = new SearchRequest("media");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(queryBuilder)
                .from(page * size)   // e.g., page 2 → offset 20 if size=10
                .size(size);

        // Sorting
        switch (sortBy) {
            case UPLOAD_DATE -> sourceBuilder.sort(new FieldSortBuilder(ContentMetaData.UPLOAD_DATE).order(sortOrder));
            case LENGTH -> sourceBuilder.sort(new FieldSortBuilder(ContentMetaData.LENGTH).order(sortOrder));
            case SIZE -> sourceBuilder.sort(SortBuilders.fieldSort(ContentMetaData.SIZE).order(sortOrder));
            case YEAR -> sourceBuilder.sort(SortBuilders.fieldSort(ContentMetaData.YEAR).order(sortOrder));
        }
        // tie-breaker then sort with score
        sourceBuilder.sort(SortBuilders.scoreSort().order(sortOrder));

        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        response.getHits().forEach(hit -> System.out.println(hit.getSourceAsString()));

        return response;
    }
}
