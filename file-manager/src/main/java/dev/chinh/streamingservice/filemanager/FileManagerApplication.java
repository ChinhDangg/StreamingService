package dev.chinh.streamingservice.filemanager;

import dev.chinh.streamingservice.filemanager.repository.FileSystemItemRepository;
import dev.chinh.streamingservice.filemanager.service.FileService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FileManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagerApplication.class, args);
    }

    //@Bean
    CommandLineRunner commandLineRunner(FileSystemItemRepository repository, FileService fileService) {
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
