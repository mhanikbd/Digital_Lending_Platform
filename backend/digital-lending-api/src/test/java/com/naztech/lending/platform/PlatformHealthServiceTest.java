package com.naztech.lending.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.naztech.lending.platform.dto.ComponentStatus;
import com.naztech.lending.platform.dto.PlatformHealthResponse;
import com.naztech.lending.storage.ObjectStorageHealthIndicator;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformHealthServiceTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;
    @Mock
    private RedisConnection redisConnection;
    @Mock
    private ObjectStorageHealthIndicator objectStorage;

    @Test
    void reportsUpOnlyWhenEveryDependencyIsReachable() throws SQLException {
        givenDatabaseIsValid(true);
        givenCacheRespondsToPing();
        when(objectStorage.health()).thenReturn(Health.up().build());

        PlatformHealthResponse response = service().check();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.components()).extracting(ComponentStatus::name)
                .containsExactly("database", "cache", "objectStorage");
        assertThat(response.components()).allMatch(ComponentStatus::isUp);
    }

    @Test
    void oneFailingDependencyStillReportsTheStateOfTheOthers() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        givenCacheRespondsToPing();
        when(objectStorage.health()).thenReturn(Health.up().build());

        PlatformHealthResponse response = service().check();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.components()).filteredOn(component -> component.name().equals("database"))
                .singleElement()
                .satisfies(component -> assertThat(component.isUp()).isFalse());
        assertThat(response.components()).filteredOn(component -> component.name().equals("cache"))
                .singleElement()
                .satisfies(component -> assertThat(component.isUp()).isTrue());
    }

    @Test
    void reportsDownWhenTheConnectionIsObtainedButNotUsable() throws SQLException {
        givenDatabaseIsValid(false);
        givenCacheRespondsToPing();
        when(objectStorage.health()).thenReturn(Health.up().build());

        assertThat(service().check().status()).isEqualTo("DOWN");
    }

    @Test
    void reportsDownWhenObjectStorageIsUnhealthy() throws SQLException {
        givenDatabaseIsValid(true);
        givenCacheRespondsToPing();
        when(objectStorage.health()).thenReturn(Health.down().build());

        assertThat(service().check().status()).isEqualTo("DOWN");
    }

    private PlatformHealthService service() {
        return new PlatformHealthService(dataSource, redisConnectionFactory, objectStorage);
    }

    private void givenDatabaseIsValid(boolean valid) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(valid);
    }

    private void givenCacheRespondsToPing() {
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
    }
}
