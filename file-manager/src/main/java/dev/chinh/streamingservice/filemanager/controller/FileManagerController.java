package dev.chinh.streamingservice.filemanager.controller;

import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/file")
public class FileManagerController {

    private final FileService fileService;

    @GetMapping("/root")
    public ResponseEntity<?> getRootDirectory(@RequestParam(required = false, name = "p") Integer page,
                                              @RequestParam(required = false, name = "by") SortBy sortBy,
                                              @RequestParam(required = false, name = "order") Sort.Direction sortOrder) {
        if (page == null) page = 0;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesAtRoot(page, sortBy, sortOrder));
    }

    @GetMapping("/dir")
    public ResponseEntity<?> getDirectoryContents(@RequestParam(required = false, name = "full") Boolean getParentInfo,
                                                  @RequestParam String id,
                                                  @RequestParam(required = false, name = "p") Integer page,
                                                  @RequestParam(required = false, name = "by") SortBy sortBy,
                                                  @RequestParam(required = false, name = "order") Sort.Direction sortOrder) {
        if (getParentInfo == null) getParentInfo = false;
        if (page == null) page = 0;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesInDirectory(getParentInfo, id, page, sortBy, sortOrder));
    }


    @PostMapping("/vid/{fileId}")
    public ResponseEntity<?> addFileAsVideo(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addFileAsVideoMedia(fileId));
    }

    @PostMapping("/album/{fileId}")
    public ResponseEntity<?> addDirectoryAsAlbum(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addDirectoryAsAlbumMedia(fileId));
    }

    @PostMapping("/grouper/{fileId}")
    public ResponseEntity<?> addDirectoryAsGrouper(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addDirectoryAsGrouperMedia(fileId));
    }


    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId) {
        fileService.initiateDeleteFile(fileId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<?> deleteMedia(@PathVariable long mediaId) {
        fileService.initiateDeleteMediaFile(mediaId);
        return ResponseEntity.ok().build();
    }


    public record CreateDirectoryRequest(@Size(max = 30) String parentId, @Size(max = 300) String directoryName) {}
    @PostMapping
    public ResponseEntity<Void> createDirectory(@RequestBody @Valid CreateDirectoryRequest request) {
        fileService.createNewFolder(request.parentId, request.directoryName);
        return ResponseEntity.ok().build();
    }


    public record InitiateMultipartUploadRequest(@NotBlank @Size(max = 1000) String filePath) {}
    @PostMapping("/upload/create-session")
    public String initiateSession(@RequestBody @Valid InitiateMultipartUploadRequest request) {
        return fileService.initiateUploadRequest(request.filePath);
    }
}
