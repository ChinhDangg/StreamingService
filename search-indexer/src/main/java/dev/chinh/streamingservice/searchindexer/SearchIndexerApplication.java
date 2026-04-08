package dev.chinh.streamingservice.searchindexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SearchIndexerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchIndexerApplication.class, args);
    }

//    @Bean
//    CommandLineRunner commandLineRunner(OpenSearchService openSearchService) {
//        return args -> {
//            for (MediaNameEntityConstant constant : MediaNameEntityConstant.values()) {
//                String oldIndex = constant.getName() + "_v1"; // use .getName() not .name()
//                String newIndex = constant.getName() + "_v2";
//                //openSearchService.createIndexWithSettingAndMapping(newIndex, "/mapping/media-name-entity-mapping.json");
//                //openSearchService.reindex(oldIndex, newIndex);
//                //openSearchService.addAliasToIndex(newIndex, constant.getName());
//                //openSearchService.verifyIndexCount(oldIndex, newIndex);
//                //openSearchService.deleteIndex(oldIndex);
//            }
////            String oldIndex = OpenSearchService.MEDIA_INDEX_NAME + "_v1";
////            String newIndex = OpenSearchService.MEDIA_INDEX_NAME + "_v2";
////            openSearchService.createIndexWithSettingAndMapping(newIndex, "/mapping/media-mapping.json");
////            openSearchService.reindex(oldIndex, newIndex);
////            openSearchService.addAliasToIndex(newIndex, OpenSearchService.MEDIA_INDEX_NAME);
////            openSearchService.verifyIndexCount(oldIndex, newIndex);
////            openSearchService.deleteIndex(oldIndex);
//        };
//    }

}
