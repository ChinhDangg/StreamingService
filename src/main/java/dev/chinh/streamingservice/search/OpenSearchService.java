package dev.chinh.streamingservice.search;

import lombok.RequiredArgsConstructor;
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
        //indexDocument();
        //search("Matrix", 0, 10, true);
        //partialUpdate();
        //scriptedUpdate();
        updateMapping("length", "integer");
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

    public void deleteDocument(String id) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        DeleteRequest request = new DeleteRequest("media", id);
        DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
        System.out.println("Deleted doc id=" + id + " result=" + response.getResult());
    }

    // http://localhost:9200/media/_doc/1?pretty
    public static void indexDocument() throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        Map<String, Object> doc = new HashMap<>();
        doc.put("title", "The Matrix 3");
        doc.put("tags", List.of("sci-fi", "action"));
        doc.put("characters", List.of("Neo", "Trinity"));
        doc.put("universes", List.of("The Matrix"));
        doc.put("authors", List.of("Wachowski Sisters"));
        doc.put("uploadDate", "1999-03-31");

        doc.put("id", 1L);
        doc.put("bucket", "3dvid");
        doc.put("parentPath", "");
        doc.put("key", "2b.mp4");
        doc.put("thumbnail", "");

        IndexRequest request = new IndexRequest("media")
                .id("1")
                .source(doc);

        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        System.out.println("Indexed with id: " + response.getId());
    }

    public static void partialUpdate() throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("tags", List.of("sci-fi", "action", "classic"));
        updateFields.put("year", 2000);

        UpdateRequest request = new UpdateRequest("media", "1")
                .doc(updateFields);

        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Document updated, result: " + response.getResult());
    }

    public static void scriptedUpdate() throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        Map<String, Object> params = new HashMap<>();
        params.put("tag", "must-watch");

        UpdateRequest request = new UpdateRequest("media", "1")
                .script(new Script(ScriptType.INLINE,
                        "painless",
                        "ctx._source.tags.add(params.tag)",
                        params));

        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Scripted update done, result: " + response.getResult());
    }

    public static void search(String searchString, int page, int size, boolean sortByYear) throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        SearchRequest searchRequest = new SearchRequest("media");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders
                .multiMatchQuery(searchString)
                        .field("title", 3.0f)
                        .field("universes", 2.0f)
                        .field("characters", 2.0f)
                        .field("tags", 1.5f)
                        .field("authors", 1.0f)
                .prefixLength(1)
                .fuzziness(Fuzziness.AUTO)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
        );

        // Pagination
        sourceBuilder.from(page * size);   // e.g., page 2 → offset 20 if size=10
        sourceBuilder.size(size);

        // Sorting
        if (sortByYear) {
            // Sort by year desc, then relevance as tie-breaker
            sourceBuilder.sort(new FieldSortBuilder("uploadDate").order(SortOrder.DESC));
            sourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        } else {
            // Default: sort by relevance
            sourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        }

        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        response.getHits().forEach(hit -> System.out.println(hit.getSourceAsString()));
    }
}
