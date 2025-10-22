package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController("/internal/access")
public class InternalController {

    private final AlbumService albumService;

    @PostMapping("/partial/{mediaId}/{resolution}")
    public void cacheMediaLastAccess(@PathVariable long mediaId, @PathVariable Resolution resolution) {
        albumService.addCacheLastAccess(albumService.getCacheMediaJobId(mediaId, resolution), null);
    }

    @PostMapping("/album-partial/{albumId}/{vidNum}/{resolution}")
    public void cacheMediaLastAccess(@PathVariable long albumId, @PathVariable int vidNum,
                                     @PathVariable Resolution resolution) {
        albumService.addCacheLastAccess(albumService.getAlbumVidCacheJobIdString(albumId, vidNum, resolution), null);
        albumService.cacheLastAccessForAlbum(albumService.getCacheMediaJobId(albumId, resolution), albumId);
    }
}
