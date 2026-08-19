package com.naztech.lending.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.storage.ObjectStorageProperties;
import com.naztech.lending.support.IntegrationTestBase;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the Spring Boot to object storage leg, using the same round trip a
 * document upload will perform: put, stat, get, remove.
 */
class ObjectStorageConnectivityIT extends IntegrationTestBase {

    private static final byte[] CONTENT = "scanned-nid-front".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectStorageProperties properties;

    @Test
    void createsTheConfiguredBucketOnStartup() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build());

        assertThat(exists).isTrue();
    }

    @Test
    void storesAndRetrievesADocumentBinary() throws Exception {
        String key = "test/nid-front.bin";

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(key)
                .stream(new ByteArrayInputStream(CONTENT), CONTENT.length, -1)
                .contentType("application/octet-stream")
                .build());

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        assertThat(stat.size()).isEqualTo(CONTENT.length);

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(properties.bucket()).object(key).build())) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }

        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
    }
}
