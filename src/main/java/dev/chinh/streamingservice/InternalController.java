package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController("/internal/access")
public class InternalController {

    private final VideoService videoService;

    @PostMapping("/partial/{mediaId}/{resolution}")
    public void cacheMediaLastAccess(@PathVariable long mediaId, @PathVariable Resolution resolution) {
        System.out.println(mediaId + "\t" + resolution);
        videoService.addCacheLastAccess(videoService.getCachePartialJobId(mediaId, resolution));
    }
}
