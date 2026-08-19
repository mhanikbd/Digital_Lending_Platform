package com.naztech.lending.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the document bucket on startup when the environment asks for it.
 *
 * <p>This is a development convenience. Production buckets are provisioned ahead
 * of deployment together with their retention and access policies, so
 * {@code dlp.storage.auto-create-bucket} is false there.
 *
 * <p>It runs after the context is ready rather than during bean creation: an
 * object store that is briefly unreachable should show up as a failing health
 * check, not as an application that refuses to start.
 */
@Component
public class ObjectStorageBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageBootstrap.class);

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    public ObjectStorageBootstrap(MinioClient minioClient, ObjectStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ensureBucketExists() {
        if (!properties.autoCreateBucket()) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (exists) {
                log.info("Object storage bucket '{}' is present", properties.bucket());
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            log.info("Created object storage bucket '{}'", properties.bucket());
        } catch (Exception ex) {
            log.warn("Could not verify or create bucket '{}': {}", properties.bucket(), ex.getMessage());
        }
    }
}
