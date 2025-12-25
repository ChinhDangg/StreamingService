package dev.chinh.streamingservice.mediaupload;

import dev.chinh.streamingservice.mediaupload.service.MinIOService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppStartRunner implements ApplicationRunner {

    private final MinIOService minIOService;

    @Override
    public void run(ApplicationArguments args) {
        if (!minIOService.bucketExists("media"))
            minIOService.createBucket("media");
        if (!minIOService.bucketExists("thumbnails"))
            minIOService.createBucket("thumbnails");
    }
}
