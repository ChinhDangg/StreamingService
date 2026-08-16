package dev.chinh.streamingservice.workers.internal;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.workers.task.service.TaskAlbumService;
import dev.chinh.streamingservice.workers.task.service.TaskVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/access")
public class InternalController {

    private final TaskAlbumService taskAlbumService;
    private final TaskVideoService taskVideoService;

    @PostMapping("/video/{mediaId}/{resolution}")
    public void cacheVideoLastAccess(@PathVariable long mediaId, @PathVariable Resolution resolution) {
        System.out.println("cached last access for video: " + mediaId);
        taskVideoService.addCacheVideoLastAccess(taskVideoService.getCacheMediaJobId(mediaId, resolution), null);
    }

    @PostMapping("/album/{albumId}/{resolution}")
    public void cacheAlbumImageLastAccess(@PathVariable long albumId, @PathVariable Resolution resolution) {
        System.out.println("cached last access for album: " + albumId);
        taskAlbumService.addCacheAlbumLastAccess(taskAlbumService.getCacheMediaJobId(albumId, resolution));
    }

    @PostMapping("/album-vid/{albumId}/{albumRes}/{vidRes}/{objectName}")
    public void cacheAlbumVideoLastAccess(@PathVariable long albumId,
                                          @PathVariable Resolution albumRes,
                                          @PathVariable Resolution vidRes,
                                          @PathVariable String objectName) {
        System.out.println("cached last access for album video: " + albumId + ":" + objectName);
        taskAlbumService.addCacheAlbumVideoLastAccess(albumId, taskAlbumService.getAlbumVidCacheJobIdString(albumId, objectName, vidRes), albumRes);
    }
}
