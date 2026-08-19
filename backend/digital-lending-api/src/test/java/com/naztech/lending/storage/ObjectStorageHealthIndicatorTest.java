package com.naztech.lending.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class ObjectStorageHealthIndicatorTest {

    private static final ObjectStorageProperties PROPERTIES = new ObjectStorageProperties(
            "http://localhost:9000", "key", "secret", "dlp-documents", false);

    @Mock
    private MinioClient minioClient;

    @Test
    void reportsUpWhenTheDocumentBucketIsReachable() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        Health health = new ObjectStorageHealthIndicator(minioClient, PROPERTIES).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "dlp-documents");
    }

    @Test
    void reportsDownWhenTheBucketIsMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        Health health = new ObjectStorageHealthIndicator(minioClient, PROPERTIES).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "bucket does not exist");
    }

    @Test
    void reportsDownWithoutLeakingCredentialsWhenTheStoreIsUnreachable() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IOException("connection refused for key=secret"));

        Health health = new ObjectStorageHealthIndicator(minioClient, PROPERTIES).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().toString()).doesNotContain("secret");
    }
}
