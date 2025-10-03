package dev.chinh.streamingservice.search;

import lombok.RequiredArgsConstructor;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.builder.SearchSourceBuilder;
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
        //searchByTitle();
        //partialUpdate();
        scriptedUpdate();
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

    // http://localhost:9200/media/_doc/1?pretty
    public static void indexDocument() throws IOException, URISyntaxException {
        String mappingJson = loadMapping("mapping/media-mapping.json");
        RestHighLevelClient client = openSearchClient();

        Map<String, Object> doc = new HashMap<>();
        doc.put("title", "The Matrix");
        doc.put("tags", List.of("sci-fi", "action"));
        doc.put("authors", List.of("Wachowski Sisters"));
        doc.put("year", 1999);
        doc.put("uploadDate", "1999-03-31");

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

    public static void searchByTitle() throws IOException, URISyntaxException {
        RestHighLevelClient client = openSearchClient();

        SearchRequest searchRequest = new SearchRequest("media");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery("title", "Matrix"));
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        response.getHits().forEach(hit -> System.out.println(hit.getSourceAsString()));
    }
}
