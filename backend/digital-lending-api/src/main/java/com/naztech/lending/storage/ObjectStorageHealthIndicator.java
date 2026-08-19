package com.naztech.lending.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports object storage reachability under {@code /actuator/health}.
 *
 * <p>The bucket is probed rather than the server root, because a reachable
 * server with a missing or unreadable bucket is still a broken document service.
 */
@Component("objectStorage")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    public ObjectStorageHealthIndicator(MinioClient minioClient, ObjectStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                return Health.down()
                        .withDetail("bucket", properties.bucket())
                        .withDetail("reason", "bucket does not exist")
                        .build();
            }
            return Health.up().withDetail("bucket", properties.bucket()).build();
        } catch (Exception ex) {
            // The message can carry endpoint detail but never credentials.
            return Health.down()
                    .withDetail("bucket", properties.bucket())
                    .withDetail("reason", ex.getClass().getSimpleName())
                    .build();
        }
    }
}
