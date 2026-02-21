package dev.chinh.streamingservice.filemanager.controller;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
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
    public ResponseEntity<?> getDirectoryContents(@RequestParam String id,
                                                  @RequestParam(required = false, name = "p") Integer page,
                                                  @RequestParam(required = false, name = "by") SortBy sortBy,
                                                  @RequestParam(required = false, name = "order") Sort.Direction sortOrder) {
        if (page == null) page = 0;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesInDirectory(id, page, sortBy, sortOrder));
    }

    @PostMapping("/vid/{fileId}")
    public ResponseEntity<?> addFileAsVideo(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addFileAsVideo(fileId));
    }

    @PostMapping("/album/{fileId}")
    public ResponseEntity<?> addDirectoryAsAlbum(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addDirectoryAsAlbum(fileId));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId) {
        fileService.initiateDeleteFile(fileId);
        return ResponseEntity.ok().build();
    }


    public record InitiateMultipartUploadRequest(String objectKey, MediaType mediaType) {}

    @PostMapping("/upload/create-session")
    public String initiateSession(@RequestBody InitiateMultipartUploadRequest request) {
        return fileService.initiateUploadRequest(request.objectKey, request.mediaType);
    }
}
