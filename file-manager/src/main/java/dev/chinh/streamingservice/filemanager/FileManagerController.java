package dev.chinh.streamingservice.filemanager;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/file")
public class FileManagerController {

    private final FileService fileService;

    @GetMapping("/root")
    public ResponseEntity<?> getRootDirectory() {
        return ResponseEntity.ok(fileService.findFilesAtRoot());
    }

    @PostMapping("/dir")
    public ResponseEntity<?> getDirectoryContents(@RequestBody String directoryId) {
        return ResponseEntity.ok(fileService.findFilesInDirectory(directoryId));
    }

    @PostMapping("/vid/{fileId}")
    public ResponseEntity<?> addFileAsVideo(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addFileAsVideo(fileId));
    }

    @PostMapping("/album/{fileId}")
    public ResponseEntity<?> addDirectoryAsAlbum(@PathVariable String fileId) {
        return ResponseEntity.ok(fileService.addDirectoryAsAlbum(fileId));
    }
}
