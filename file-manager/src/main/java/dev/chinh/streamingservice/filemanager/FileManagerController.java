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
}
