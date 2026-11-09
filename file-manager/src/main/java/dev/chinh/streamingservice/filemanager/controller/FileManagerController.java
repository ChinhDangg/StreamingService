package dev.chinh.streamingservice.filemanager.controller;

import dev.chinh.streamingservice.filemanager.model.FileResponse;
import dev.chinh.streamingservice.filemanager.service.FileFindService;
import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import dev.chinh.streamingservice.filemanager.service.FileTrashService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/file")
public class FileManagerController {

    private final FileService fileService;
    private final FileFindService fileFindService;
    private final FileTrashService fileTrashService;

    @GetMapping("/root")
    public ResponseEntity<FileResponse<?>> getRootDirectory(@RequestParam(required = false, name = "p") String pageCursor,
                                                            @RequestParam(required = false, name = "by") SortBy sortBy,
                                                            @RequestParam(required = false, name = "order") Sort.Direction sortOrder,
                                                            @AuthenticationPrincipal Jwt jwt) {
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(FileResponse.ok(fileFindService.findFilesAtRoot(jwt.getSubject(), pageCursor, sortBy, sortOrder)));
    }

    @GetMapping("/dir")
    public ResponseEntity<FileResponse<?>> getDirectoryContents(@RequestParam(required = false, name = "full") Boolean getParentInfo,
                                                  @RequestParam String id,
                                                  @RequestParam(required = false, name = "p") String pageCursor,
                                                  @RequestParam(required = false, name = "by") SortBy sortBy,
                                                  @RequestParam(required = false, name = "order") Sort.Direction sortOrder,
                                                  @AuthenticationPrincipal Jwt jwt) {
        if (getParentInfo == null) getParentInfo = false;
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(FileResponse.ok(fileFindService.findFilesInDirectory(jwt.getSubject(), getParentInfo, id, pageCursor, sortBy, sortOrder)));
    }

    public record SearchFilesRequest(
            @NotBlank @Size(max = 30) String parentId,
            @NotBlank @Size(max = 300) String searchString,
            boolean isRecursive,
            String pageCursor
    ) {}
    @PostMapping("/search")
    public ResponseEntity<FileResponse<?>> searchFiles(@Valid @RequestBody SearchFilesRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileFindService.searchFileByName(jwt.getSubject(), request.parentId, request.searchString, request.isRecursive, request.pageCursor)));
    }


    @PostMapping("/vid/{fileId}")
    public ResponseEntity<FileResponse<?>> addFileAsVideo(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileService.addFileAsVideoMedia(jwt.getSubject(), fileId, null)));
    }

    @PostMapping("/album/{fileId}")
    public ResponseEntity<FileResponse<?>> addDirectoryAsAlbum(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileService.addDirectoryAsAlbumMedia(jwt.getSubject(), fileId)));
    }

    @PostMapping("/grouper/{fileId}")
    public ResponseEntity<FileResponse<?>> addDirectoryAsGrouper(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileService.addDirectoryAsGrouperMedia(jwt.getSubject(), fileId)));
    }

    @GetMapping("/trash")
    public ResponseEntity<FileResponse<?>> getTrash(@RequestParam(required = false, name = "p") String pageCursor,
                                      @RequestParam(required = false, name = "by") SortBy sortBy,
                                      @RequestParam(required = false, name = "order") Sort.Direction sortOrder,
                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileFindService.findFilesInTrash(jwt.getSubject(), pageCursor, sortBy, sortOrder)));
    }

    @PostMapping("/trash")
    public ResponseEntity<FileResponse<?>> moveFilesToTrash(@RequestBody List<String> fileIds, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileTrashService.moveToTrash(jwt.getSubject(), fileIds)));
    }

    @PostMapping("/restore")
    public ResponseEntity<FileResponse<?>> moveFilesOutOfTrash(@RequestBody List<String> fileIds, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(FileResponse.ok(fileTrashService.moveOutOfTrash(jwt.getSubject(), fileIds, Optional.empty())));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<FileResponse<?>> deleteFile(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        fileTrashService.initiateDeleteFile(jwt.getSubject(), fileId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<FileResponse<?>> deleteMedia(@PathVariable long mediaId, @AuthenticationPrincipal Jwt jwt) {
        fileTrashService.initiateDeleteMediaFile(jwt.getSubject(), mediaId);
        return ResponseEntity.noContent().build();
    }


    public record CreateDirectoryRequest(@NotBlank @Size(max = 30) String parentId, @NotBlank @Size(max = 300) String name) {}
    @PostMapping("/folder")
    public ResponseEntity<FileResponse<?>> createDirectory(@RequestBody @Valid CreateDirectoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(fileService.createNewDirectory(jwt.getSubject(), request.parentId, request.name)));
    }

    @PutMapping("/rename")
    public ResponseEntity<FileResponse<?>> renameFile(@RequestBody @Valid CreateDirectoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(fileService.renameFileItem(jwt.getSubject(), request.parentId, request.name)));
    }

    public record MoveFileRequest(@NotBlank @Size(max = 30) String fileId, @NotBlank @Size(max = 30) String parentId) {}
    @PutMapping("/move")
    public ResponseEntity<FileResponse<?>> moveFile(@RequestBody @Valid MoveFileRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(fileService.initiateMoveFileItem(jwt.getSubject(), request.fileId, request.parentId)));
    }


    @GetMapping("/download/{fileId}")
    public ResponseEntity<FileResponse<?>> getFileObjectUrl(@PathVariable String fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(FileResponse.ok(fileService.getFileObjectUrl(jwt.getSubject(), fileId)));
    }
}
