package dev.chinh.streamingservice.filemanager;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@SpringBootApplication
public class FileManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagerApplication.class, args);
    }

    //@Bean
    CommandLineRunner commandLineRunner(FileSystemRepository repository, FileService fileService) {
        return args -> {
//            repository.save(new FileSystemItem(
//                    1,
//                    "media",
//                    "vid",
//                    FileType.DIR,
//                    LocalDateTime.now()
//            ));
//            repository.save(new FileSystemItem(
//                    2,
//                    "media/vid",
//                    "vid.mp4",
//                    FileType.FILE,
//                    LocalDateTime.now()
//            ));
        };
    }

}
