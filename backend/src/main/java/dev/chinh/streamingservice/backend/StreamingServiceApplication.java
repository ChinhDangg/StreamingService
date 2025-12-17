package dev.chinh.streamingservice.backend;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.backend.content.service.AlbumService;
import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.backend.data.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.backend.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.backend.data.repository.MediaTagRepository;
import dev.chinh.streamingservice.backend.data.service.ThumbnailService;
import dev.chinh.streamingservice.backend.event.MediaEventProducer;
import dev.chinh.streamingservice.backend.search.service.MediaSearchService;
import dev.chinh.streamingservice.backend.upload.service.MediaUploadService;
import dev.chinh.streamingservice.backend.modify.service.NameEntityModifyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StreamingServiceApplication {

    static {
        try {
            OSUtil._init();
            OSUtil.startDockerCompose();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start init OSUtil", e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(MediaSearchService mediaSearchService, AlbumService albumService, MinIOService minIOService, NameEntityModifyService nameEntityModifyService, MediaMetaDataRepository mediaMetaDataRepository, MediaGroupMetaDataRepository mediaGroupMetaDataRepository, MediaUploadService mediaUploadService, ThumbnailService thumbnailService, MediaTagRepository mediaTagRepository, MediaEventProducer mediaEventProducer) {
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














