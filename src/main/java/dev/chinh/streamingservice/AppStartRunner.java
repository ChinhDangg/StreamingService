package dev.chinh.streamingservice;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean ramCreated = OSUtil.createRamDisk();

        if (!ramCreated) {
            System.out.println("Fail to create RAM DISK");
            return;
        }

        OSUtil.startDockerCompose();
    }
}
