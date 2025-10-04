package dev.chinh.streamingservice.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.search.MediaSearchItem;
import dev.chinh.streamingservice.search.MediaSearchResult;
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
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final RestHighLevelClient client;
    private final ObjectMapper mapper;

    public static void main(String[] args) throws IOException, URISyntaxException {
        //createIndexWithMapping();

//        Map<String, Object> doc = new HashMap<>();
//        doc.put("title", "The Matrix 1");
//        doc.put("tags", List.of("sci-fi", "action"));
//        doc.put("characters", List.of("Neo", "Trinity"));
//        doc.put("universes", List.of("The Matrix"));
//        doc.put("authors", List.of("Wachowski Sisters"));
//        doc.put("uploadDate", "1997-03-31");
//        doc.put("year", 1997)

//        doc.put("id", 3L);
//        doc.put("bucket", "3dvid");
//        doc.put("parentPath", "");
//        doc.put("key", "2b.mp4");
//        doc.put("thumbnail", "");
//        doc.put("length", 2400);
//        indexDocument(3L, doc);

        //System.out.println(search("Matrix", 0, 10, true, SortOrder.DESC));
        //searchMatchByOneField("1999-03-31", "uploadDate", 0, 10, true, SortOrder.DESC);
        //searchTermByOneField(List.of("1999"), "year", 0, 10, true, SortOrder.DESC);

//        Map<String, Collection<Object>> fieldValues = new HashMap<>();
//        fieldValues.put("title", List.of("The Matrix"));
//        fieldValues.put("characters", List.of("Neo"));
//        advanceSearch(fieldValues, 0, 10, true, SortOrder.DESC);

//        Map<String, Object> updateFields = new HashMap<>();
//        updateFields.put("tags", List.of("tag1", "tag2"));
//        partialUpdateDocument(updateFields);

        //addValueToFieldInDocument(1, "tags", "tag3");

        //updateMapping("year", "integer");
        //deleteDocument(1);
    }

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
    public void partialUpdateDocument(Map<String, Object> updateFields) throws IOException {
        UpdateRequest request = new UpdateRequest("media", "1")
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

    public MediaSearchResult advanceSearch(Map<String, Collection<Object>> fieldValues, int page, int size,
                              boolean sortByYear, SortOrder sortOrder) throws IOException {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        for (Map.Entry<String, Collection<Object>> entry : fieldValues.entrySet()) {
            String field = entry.getKey();
            Collection<Object> values = entry.getValue();

            if (values == null || values.isEmpty()) {
                continue;
            }

            if (values.size() == 1) {
                Object value = values.iterator().next();
                if (value instanceof String) {
                    // text type with keyword type can't use termQuery
                    boolQuery.must(QueryBuilders.matchQuery(field, value));
                } else {
                    boolQuery.must(QueryBuilders.termQuery(field, value));
                }
            } else { // Group multiple values for the same field with OR
                BoolQueryBuilder innerOr = QueryBuilders.boolQuery();
                for (Object value : values) {
                    if (value instanceof String) {
                        innerOr.should(QueryBuilders.matchQuery(field, value));
                    } else {
                        innerOr.should(QueryBuilders.termQuery(field, value));
                    }
                }
                boolQuery.must(innerOr); // must match one of the values for this field
            }
        }

        return searchWithQueryBuilder(boolQuery, page, size, sortByYear, sortOrder);
    }

    public MediaSearchResult search(Object text, int page, int size, boolean sortByYear,
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

        return searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    public MediaSearchResult searchMatchByOneField(String field, Object text, int page, int size,
                                                   boolean sortByYear, SortOrder sortOrder) throws IOException {
        QueryBuilder q = QueryBuilders.matchQuery(field, text);
        return searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    /**
     * Search exactly with given search strings by field.
     * Does not work with fields that have text type. Use search match for that.
     */
    public MediaSearchResult searchTermByOneField(String field, Collection<Object> text, int page, int size,
                                            boolean sortByYear, SortOrder sortOrder) throws IOException {
        QueryBuilder q = QueryBuilders.termsQuery(field, text);
        return searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    private MediaSearchResult searchWithQueryBuilder(QueryBuilder queryBuilder, int page, int size, boolean sortByYear,
                                               SortOrder sortOrder) throws IOException {

        SearchRequest searchRequest = new SearchRequest("media");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(queryBuilder);

        // Pagination
        sourceBuilder.from(page * size);   // e.g., page 2 → offset 20 if size=10
        sourceBuilder.size(size);

        // Sorting
        if (sortByYear) {
            // Sort by year desc, then relevance as tie-breaker
            sourceBuilder.sort(new FieldSortBuilder("uploadDate").order(sortOrder));
            sourceBuilder.sort(SortBuilders.scoreSort().order(sortOrder));
        } else {
            // Default: sort by relevance
            sourceBuilder.sort(SortBuilders.scoreSort().order(sortOrder));
        }

        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        response.getHits().forEach(hit -> System.out.println(hit.getSourceAsString()));

        return mapResponseToMediaSearchResult(response, page, size);
    }

    private MediaSearchResult mapResponseToMediaSearchResult(SearchResponse response, int page, int size) {
        List<MediaSearchItem> items = new ArrayList<>();

        for (SearchHit hit : response.getHits()) {
            MediaSearchItem searchItem = mapper.convertValue(hit.getSourceAsMap(), MediaSearchItem.class);
            items.add(searchItem);
        }

        MediaSearchResult result = new MediaSearchResult(items);
        result.setPage(page);
        result.setPageSize(size);
        result.setTotal(Objects.requireNonNull(response.getHits().getTotalHits()).value());
        result.setTotalPages((result.getTotal() + size -1) / size);

        return result;
    }
}
