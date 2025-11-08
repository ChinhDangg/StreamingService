package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class StreamingServiceApplication {

    static {
        try {
            OSUtil.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start init OSUtil", e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(OpenSearchService openSearchService,
                                               ScheduleService scheduleService, RedisTemplate<String, Object> redisTemplate, AlbumService albumService) {
        return args -> {
//            Map<String, Object> document = new HashMap<>();
//            document.put("title", "Test Video Sample 1");
//            document.put("universes", List.of("Nier Automata"));
//            document.put("bucket", "3dvid");
//            document.put("parentPath", "");
//            document.put("key", "2b.mp4");
//            document.put("uploadDate", LocalDate.now());
//            document.put("length", 657);
//            document.put("size", 400111222);
//            document.put("width", 1920);
//            document.put("height", 1080);
//            document.put("id", 1);
//            openSearchService.indexDocument(1, document);
            //openSearchService.deleteDocument(1);

//            Map<String, Object> document = new HashMap<>();
//            document.put("title", "Test Album");
//            document.put("universes", List.of("Genshin Impact"));
//            document.put("bucket", "cos");
//            document.put("parentPath", "aqua/legion/AA - Jade - HonkaiStar Rail");
//            document.put("thumbnail", "aqua/legion/AA - Jade - HonkaiStar Rail/水淼Aqua cosplay Jade - HonkaiStar Rail (1).jpg");
//            document.put("uploadDate", LocalDate.now());
//            document.put("year", 2025);
//            document.put("length", 92);
//            document.put("size", 180111222);
//            document.put("id", 2);
//            openSearchService.indexDocument(2, document);

            //openSearchService.createIndexWithMapping();

//            String oldIndex = OpenSearchService.INDEX_NAME + "_v1";
//            String newIndex = OpenSearchService.INDEX_NAME + "_v2";
//            openSearchService.createIndexWithMapping(newIndex);
//            openSearchService.reindex(oldIndex, newIndex);
//            openSearchService.addAliasToIndex(newIndex, OpenSearchService.INDEX_NAME);
//            openSearchService.verifyIndexCount(oldIndex, newIndex);
//            openSearchService.deleteIndex(oldIndex);
        };
    }
}
