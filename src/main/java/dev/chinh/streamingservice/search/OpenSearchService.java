package dev.chinh.streamingservice.search;

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
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.chinh.streamingservice.search.OpenSearchConfig.loadMapping;
import static dev.chinh.streamingservice.search.OpenSearchConfig.openSearchClient;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    //private final RestHighLevelClient client;

    public static void main(String[] args) throws IOException, URISyntaxException {
        //createIndexWithMapping();

//        Map<String, Object> doc = new HashMap<>();
//        doc.put("title", "The Matrix 3");
//        doc.put("tags", List.of("sci-fi", "action"));
//        doc.put("characters", List.of("Neo", "Trinity"));
//        doc.put("universes", List.of("The Matrix"));
//        doc.put("authors", List.of("Wachowski Sisters"));
//        doc.put("uploadDate", "1999-03-31");
//
//        doc.put("id", 1L);
//        doc.put("bucket", "3dvid");
//        doc.put("parentPath", "");
//        doc.put("key", "2b.mp4");
//        doc.put("thumbnail", "");
//        doc.put("length", 2400);
//        indexDocument(1L, doc);

        //search("Matrix", 0, 10, true, SortOrder.DESC);
        //searchMatchByOneField("The Matrix 3", "title", 0, 10, true, SortOrder.DESC);
        //searchTermByOneField(List.of("The Matrix 3"), "title", 0, 10, true, SortOrder.DESC);


//        Map<String, Object> updateFields = new HashMap<>();
//        updateFields.put("tags", List.of("tag1", "tag2"));
//        partialUpdateDocument(updateFields);

        //addValueToFieldInDocument(1, "tags", "tag3");

        //updateMapping("length", "integer");
        //deleteDocument(1);
    }

    // http://localhost:9200/media
    public static void createIndexWithMapping() throws IOException, URISyntaxException {
        String mappingJson = loadMapping("mapping/media-mapping.json");
        RestHighLevelClient client = openSearchClient();

        CreateIndexRequest request = new CreateIndexRequest("media");
        request.mapping(mappingJson, XContentType.JSON);

        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

        System.out.println("Index created: " + createIndexResponse.index());
    }

    public static void updateMapping(String fieldName, String fieldType) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

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

    public static void deleteDocument(long id) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        DeleteRequest request = new DeleteRequest("media", String.valueOf(id));
        DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
        System.out.println("Deleted doc id=" + id + " result=" + response.getResult());
    }

    // http://localhost:9200/media/_doc/1?pretty

    /**
     * Add a new doc to the index with given id.
     * Will only pass if the id doesn't exist yet.
     */
    public static void indexDocument(long id, Map<String, Object> doc) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

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
    public static void partialUpdateDocument(Map<String, Object> updateFields) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        UpdateRequest request = new UpdateRequest("media", "1")
                .doc(updateFields);

        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Document updated, result: " + response.getResult());
    }

    /**
     * Adding new values to one existing field for given document id.
     * @param id the id of the doc.
     */
    public static void addValueToFieldInDocument(long id, String field, Object values) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

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

    public static void search(String searchString, int page, int size, boolean sortByYear, SortOrder sortOrder) throws IOException, URISyntaxException {
        QueryBuilder q = QueryBuilders
                .multiMatchQuery(searchString)
                .field("title", 3.0f)
                .field("universes", 2.0f)
                .field("characters", 2.0f)
                .field("tags", 1.5f)
                .field("authors", 1.0f)
                .prefixLength(1)
                .fuzziness(Fuzziness.AUTO)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);

        searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    public static void searchMatchByOneField(String searchString, String field, int page, int size, boolean sortByYear,
                                             SortOrder sortOrder) throws IOException, URISyntaxException {
        QueryBuilder q = QueryBuilders.matchQuery(field, searchString);
        searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    /**
     * Search exactly with given search strings by field.
     * Does not work with fields that have text type. Use search match for that.
     */
    public static void searchTermByOneField(Collection<String> searchStrings, String field, int page, int size,
                                            boolean sortByYear, SortOrder sortOrder) throws IOException, URISyntaxException {
        QueryBuilder q = QueryBuilders.termsQuery(field, searchStrings);
        searchWithQueryBuilder(q, page, size, sortByYear, sortOrder);
    }

    private static void searchWithQueryBuilder(QueryBuilder queryBuilder, int page, int size, boolean sortByYear,
                                               SortOrder sortOrder) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

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
    }
}
