package dev.chinh.streamingservice;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AppStartRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        OSUtil.startDockerCompose();
        OSUtil.initializeRAMInfo();
    }
}
