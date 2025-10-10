package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class StreamingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(OpenSearchService openSearchService) {
        return args -> {
            Map<String, Object> document = new HashMap<>();
            document.put("title", "Test Video Sample 1");
            document.put("universes", List.of("Nier Automata"));
            document.put("bucket", "3dvid");
            document.put("parentPath", "");
            document.put("key", "2b.mp4");
            document.put("length", 657);
            document.put("width", 1920);
            document.put("height", 1080);
            document.put("id", 1);
            //openSearchService.indexDocument(1, document);
        };
    }
}
