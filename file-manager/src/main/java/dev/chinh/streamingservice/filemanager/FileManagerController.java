package dev.chinh.streamingservice.filemanager;

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
    public ResponseEntity<?> getRootDirectory(@RequestParam(required = false) int offset,
                                              @RequestParam(required = false) SortBy sortBy,
                                              @RequestParam(required = false) Sort.Direction sortOrder) {
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesAtRoot(offset, sortBy, sortOrder));
    }

    @PostMapping("/dir")
    public ResponseEntity<?> getDirectoryContents(@RequestBody String directoryId,
                                                  @RequestParam(required = false) int offset,
                                                  @RequestParam(required = false) SortBy sortBy,
                                                  @RequestParam(required = false) Sort.Direction sortOrder) {
        if (sortBy == null) sortBy = SortBy.NAME;
        if (sortOrder == null) sortOrder = Sort.Direction.ASC;
        return ResponseEntity.ok(fileService.findFilesInDirectory(directoryId, offset, sortBy, sortOrder));
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
}
