package dev.chinh.streamingservice.backend;

import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.content.service.AlbumService;
import dev.chinh.streamingservice.backend.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/access")
public class InternalController {

    private final AlbumService albumService;
    private final VideoService videoService;

    @PostMapping("/video/{mediaId}/{resolution}")
    public void cacheVideoLastAccess(@PathVariable long mediaId, @PathVariable Resolution resolution) {
        System.out.println("cached last access for video: " + mediaId);
        videoService.addCacheVideoLastAccess(videoService.getCacheMediaJobId(mediaId, resolution), null);
    }

    @PostMapping("/album/{albumId}/{resolution}")
    public void cacheAlbumImageLastAccess(@PathVariable long albumId, @PathVariable Resolution resolution) {
        System.out.println("cached last access for album: " + albumId);
        albumService.addCacheAlbumLastAccess(albumService.getCacheMediaJobId(albumId, resolution));
    }

    @PostMapping("/album-vid/{albumId}/{albumRes}/{vidRes}/{objectName}")
    public void cacheAlbumVideoLastAccess(@PathVariable long albumId,
                                          @PathVariable Resolution albumRes,
                                          @PathVariable Resolution vidRes,
                                          @PathVariable String objectName) {
        System.out.println("cached last access for album video: " + albumId + ":" + objectName);
        albumService.addCacheAlbumVideoLastAccess(albumId, albumService.getAlbumVidCacheJobIdString(albumId, objectName, vidRes), albumRes);
    }
}
