package dev.chinh.streamingservice.filemanager;

import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.service.FileTrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppStartRunner implements ApplicationRunner {

    private final FileService fileService;
    private final FileTrashService fileTrashService;

    @Override
    public void run(ApplicationArguments args) {
        fileService.createRootFolder();
        fileTrashService.createTrashFolder();
    }
}
