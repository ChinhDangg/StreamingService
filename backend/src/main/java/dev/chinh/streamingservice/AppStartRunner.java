package dev.chinh.streamingservice;

import dev.chinh.streamingservice.search.service.MediaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppStartRunner implements ApplicationRunner {

    private final MediaSearchService mediaSearchService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        mediaSearchService._initializeIndexes();
    }
}
