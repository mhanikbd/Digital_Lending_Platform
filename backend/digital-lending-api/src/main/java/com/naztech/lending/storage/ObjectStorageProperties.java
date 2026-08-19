package com.naztech.lending.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the S3-compatible object store that holds document
 * binaries. The database only ever stores metadata and the storage key.
 *
 * @param endpoint          base URL of the object store
 * @param accessKey         access key; supplied by secret, never logged
 * @param secretKey         secret key; supplied by secret, never logged
 * @param bucket            bucket holding lending documents
 * @param autoCreateBucket  create the bucket on startup; development convenience only,
 *                          production buckets are provisioned with their policies up front
 */
@Validated
@ConfigurationProperties(prefix = "dlp.storage")
public record ObjectStorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        boolean autoCreateBucket) {
}
