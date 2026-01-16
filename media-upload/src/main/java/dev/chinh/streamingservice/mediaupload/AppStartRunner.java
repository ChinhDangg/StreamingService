package dev.chinh.streamingservice.mediaupload;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
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
        if (!minIOService.bucketExists(ContentMetaData.MEDIA_BUCKET))
            minIOService.createBucket(ContentMetaData.MEDIA_BUCKET);
        if (!minIOService.bucketExists(ContentMetaData.THUMBNAIL_BUCKET))
            minIOService.createBucket(ContentMetaData.THUMBNAIL_BUCKET);
        if (!minIOService.bucketExists(ContentMetaData.PREVIEW_BUCKET))
            minIOService.createBucket(ContentMetaData.PREVIEW_BUCKET);
    }
}
