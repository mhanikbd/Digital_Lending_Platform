package com.naztech.lending.storage;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the S3-compatible client used for all document binaries. */
@Configuration
public class ObjectStorageConfig {

    @Bean
    MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
