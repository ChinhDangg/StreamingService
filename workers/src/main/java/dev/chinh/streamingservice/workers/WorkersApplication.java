package dev.chinh.streamingservice.workers;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.workers.service.WorkerRedisService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WorkersApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkersApplication.class, args);
    }

    @Bean
    CommandLineRunner startWorkers(ApplicationContext ctx) {
        return _ -> {
            OSUtil._init();

            WorkerRedisService workerRedisService = ctx.getBean(WorkerRedisService.class);
            workerRedisService.initializeTokens(VideoWorker.TOKEN_KEY, 2);
            workerRedisService.initializeTokens(AlbumWorker.TOKEN_KEY, 2);

            int workerCount = 2;
            createAndStartWorkers(ctx, VideoWorker.class, workerCount, "video-worker");
            createAndStartWorkers(ctx, AlbumWorker.class, workerCount, "album-worker");
        };
    }

    public <T extends Worker> void createAndStartWorkers(ApplicationContext ctx, Class<T> workerClass, int numberOfWorker, String workerGroupName) {
        for (int i = 0; i < numberOfWorker; i++) {
            T clazz = ctx.getBean(workerClass);
            Thread t = new Thread(clazz, workerGroupName + "_" + i);
            t.setDaemon(true);
            t.start();
        }
        System.out.println("Started " + numberOfWorker + " " + workerGroupName + " worker threads.");
    }

}
