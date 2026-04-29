package dev.chinh.streamingservice.filemanager.controller;

import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/file")
public class FileManagerController {

    private final FileService fileService;

    @GetMapping("/root")
    public ResponseEntity<?> getRootDirectory(@RequestParam(required = false, name = "p") Integer page,
                                              @RequestParam(required = false, name = "by") SortBy sortBy,
                                              @RequestParam(required = false, name = "order") Sort.Direction sortOrder,
                                              @AuthenticationPrincipal Jwt jwt) {
        if (page == null) page = 0;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesAtRoot(jwt.getSubject(), page, sortBy, sortOrder));
    }

    @GetMapping("/dir")
    public ResponseEntity<?> getDirectoryContents(@RequestParam(required = false, name = "full") Boolean getParentInfo,
                                                  @RequestParam String id,
                                                  @RequestParam(required = false, name = "p") Integer page,
                                                  @RequestParam(required = false, name = "by") SortBy sortBy,
                                                  @RequestParam(required = false, name = "order") Sort.Direction sortOrder,
                                                  @AuthenticationPrincipal Jwt jwt) {
        if (getParentInfo == null) getParentInfo = false;
        if (page == null) page = 0;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesInDirectory(jwt.getSubject(), getParentInfo, id, page, sortBy, sortOrder));
    }

    public record SearchFilesRequest(
            @NotBlank @Size(max = 30) String parentId,
            @NotBlank @Size(max = 300) String searchString,
            boolean isRecursive,
            int page
    ) {}
    @PostMapping("/search")
    public ResponseEntity<?> searchFiles(@Valid @RequestBody SearchFilesRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(fileService.searchFileByName(jwt.getSubject(), request.parentId, request.searchString, request.isRecursive, request.page));
    }


    @PostMapping("/vid/{fileId}")
    public ResponseEntity<?> addFileAsVideo(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(fileService.addFileAsVideoMedia(jwt.getSubject(), fileId));
    }

    @PostMapping("/album/{fileId}")
    public ResponseEntity<?> addDirectoryAsAlbum(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(fileService.addDirectoryAsAlbumMedia(jwt.getSubject(), fileId));
    }

    @PostMapping("/grouper/{fileId}")
    public ResponseEntity<?> addDirectoryAsGrouper(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(fileService.addDirectoryAsGrouperMedia(jwt.getSubject(), fileId));
    }


    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        fileService.initiateDeleteFile(jwt.getSubject(), fileId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<?> deleteMedia(@PathVariable long mediaId, @AuthenticationPrincipal Jwt jwt) {
        fileService.initiateDeleteMediaFile(jwt.getSubject(), mediaId);
        return ResponseEntity.ok().build();
    }


    public record CreateDirectoryRequest(@NotBlank @Size(max = 30) String parentId, @NotBlank @Size(max = 300) String name) {}
    @PostMapping("/folder")
    public ResponseEntity<?> createDirectory(@RequestBody @Valid CreateDirectoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(fileService.createNewDirectory(jwt.getSubject(), request.parentId, request.name));
    }

    @PutMapping("/rename")
    public ResponseEntity<?> renameFile(@RequestBody @Valid CreateDirectoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(fileService.renameFileItem(jwt.getSubject(), request.parentId, request.name));
    }

    public record MoveFileRequest(@NotBlank @Size(max = 30) String fileId, @NotBlank @Size(max = 30) String parentId) {}
    @PutMapping("/move")
    public ResponseEntity<?> moveFile(@RequestBody @Valid MoveFileRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(fileService.initiateMoveFileItem(jwt.getSubject(), request.fileId, request.parentId));
    }


    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> getFileObjectUrl(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(fileService.getFileObjectUrl(jwt.getSubject(), fileId));
    }

    public record InitiateMultipartUploadRequest(@NotBlank @Size(max = 1000) String filePath) {}
    @PostMapping("/upload/create-session")
    public String initiateSession(@RequestBody @Valid InitiateMultipartUploadRequest request, @AuthenticationPrincipal Jwt jwt) {
        return fileService.initiateUploadRequest(jwt.getSubject(), request.filePath);
    }
}
