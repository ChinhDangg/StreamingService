package dev.chinh.streamingservice.backend;

import dev.chinh.streamingservice.common.OSUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StreamingServiceApplication {

    static {
        try {
            String ramBytesStr = System.getenv("RAM_VOLUME_BYTES");
            long ramBytes;
            try {
                ramBytes = Math.max(536_870_912L, Long.parseLong(ramBytesStr));
            } catch (Exception _) {
                ramBytes = 536_870_912L;
            }

            OSUtil._init();
            OSUtil._createRamDisk(ramBytes);
            OSUtil.startDockerCompose();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start init OSUtil", e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
//            openSearchService.createIndexWithMapping("media_v1", "/mapping/media-mapping.json");
//            openSearchService.addAliasToIndex("media_v1", OpenSearchService.MEDIA_INDEX_NAME);

//            String oldIndex = OpenSearchService.MEDIA_INDEX_NAME + "_v1";
//            String newIndex = OpenSearchService.MEDIA_INDEX_NAME + "_v2";
//            openSearchService.createIndexWithMapping(newIndex, "/mapping/media-mapping.json");
//            openSearchService.reindex(oldIndex, newIndex);
//            openSearchService.addAliasToIndex(newIndex, OpenSearchService.MEDIA_INDEX_NAME);
//            openSearchService.verifyIndexCount(oldIndex, newIndex);
//            openSearchService.deleteIndex(oldIndex);

//            openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, 2);
//            mediaMetaDataRepository.deleteById(2L);

//            openSearchService.deleteIndex(ContentMetaData.CHARACTERS);
//            openSearchService.deleteIndex(ContentMetaData.UNIVERSES);
//            openSearchService.deleteIndex(ContentMetaData.AUTHORS);
//            openSearchService.deleteIndex(ContentMetaData.TAGS);

//            openSearchService.createIndexWithSettingAndMapping(ContentMetaData.CHARACTERS, "/mapping/media-name-entity-mapping.json");
//            openSearchService.createIndexWithSettingAndMapping(ContentMetaData.UNIVERSES, "/mapping/media-name-entity-mapping.json");
//            openSearchService.createIndexWithSettingAndMapping(ContentMetaData.AUTHORS, "/mapping/media-name-entity-mapping.json");
//            openSearchService.createIndexWithSettingAndMapping(ContentMetaData.TAGS, "/mapping/media-name-entity-mapping.json");

//            Map<String, Property> groupInfoSubProperties = new HashMap<>();
//
//            groupInfoSubProperties.put("id", Property.of(p -> p
//                    .long_(LongNumberProperty.builder().build())
//            ));
//
//            groupInfoSubProperties.put("numInfo", Property.of(p -> p
//                    .integer(i -> i.index(false))
//            ));
//
//            ObjectProperty groupInfoProperty = new ObjectProperty.Builder()
//                    .properties(groupInfoSubProperties)
//                    .build();
//
//            Map<String, Property> topLevelProperties = new HashMap<>();
//            topLevelProperties.put("groupInfo", Property.of(p -> p.object(groupInfoProperty)));
//            openSearchService.updateIndexMapping(OpenSearchService.MEDIA_INDEX_NAME, topLevelProperties);
        };

    }
}














