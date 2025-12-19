package dev.chinh.streamingservice.backend.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinIOConfig {

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}

// enable Path-Style access for S3Client AND S3Presigner
//AWS SDK will generate:
//
//POST https://mybucket.minio.local:9000/test-vid.mp4?uploads
//
//
//MinIO doesn’t understand that → returns 400.
//
//With path-style
//POST https://localhost:9000/mybucket/test-vid.mp4?uploads

