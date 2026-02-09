package dev.chinh.streamingservice.filemanager;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppStartRunner implements ApplicationRunner {

    private final FileService fileService;

    @Override
    public void run(ApplicationArguments args) {
        fileService.createRootFolder();
    }
}
