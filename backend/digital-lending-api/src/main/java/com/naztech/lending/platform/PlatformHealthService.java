package com.naztech.lending.platform;

import com.naztech.lending.platform.dto.ComponentStatus;
import com.naztech.lending.platform.dto.PlatformHealthResponse;
import com.naztech.lending.storage.ObjectStorageHealthIndicator;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * Probes the three infrastructure dependencies the platform cannot run without.
 *
 * <p>Each probe is isolated: one dependency being down must not hide the state of
 * the others, which is the whole point of this endpoint during environment setup.
 */
@Service
public class PlatformHealthService {

    private static final Logger log = LoggerFactory.getLogger(PlatformHealthService.class);

    private static final int DATABASE_PROBE_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectStorageHealthIndicator objectStorageHealthIndicator;

    public PlatformHealthService(DataSource dataSource,
                                 RedisConnectionFactory redisConnectionFactory,
                                 ObjectStorageHealthIndicator objectStorage) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.objectStorageHealthIndicator = objectStorage;
    }

    public PlatformHealthResponse check() {
        return PlatformHealthResponse.from(List.of(checkDatabase(), checkCache(), checkObjectStorage()));
    }

    private ComponentStatus checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_PROBE_TIMEOUT_SECONDS)
                    ? ComponentStatus.up("database")
                    : ComponentStatus.down("database", "connection is not valid");
        } catch (Exception ex) {
            log.warn("Database probe failed: {}", ex.getMessage());
            return ComponentStatus.down("database", "not reachable");
        }
    }

    private ComponentStatus checkCache() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            return ComponentStatus.up("cache");
        } catch (Exception ex) {
            log.warn("Cache probe failed: {}", ex.getMessage());
            return ComponentStatus.down("cache", "not reachable");
        }
    }

    private ComponentStatus checkObjectStorage() {
        Status status = objectStorageHealthIndicator.health().getStatus();
        return Status.UP.equals(status)
                ? ComponentStatus.up("objectStorage")
                : ComponentStatus.down("objectStorage", "not reachable");
    }
}
