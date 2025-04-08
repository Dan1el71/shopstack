package com.shopstack.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Smoke tests for the infrastructure the suite depends on: Flyway migrations, the
 * Redis container used by the cache layer and the actuator health endpoint.
 */
class InfrastructureIT extends AbstractIT {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void flywayAppliedEveryMigrationAndSeededReferenceData() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(applied).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isGreaterThan(0);
    }

    @Test
    void redisContainerIsReachable() {
        String key = unique("it:smoke");
        redisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(30));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
    }

    @Test
    void actuatorHealthIsUp() {
        given()
                .get("http://localhost:" + port + "/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
