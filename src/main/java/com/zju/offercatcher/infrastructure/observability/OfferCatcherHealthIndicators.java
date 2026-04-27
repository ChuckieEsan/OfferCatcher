package com.zju.offercatcher.infrastructure.observability;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.QdrantOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 基础设施健康检查。
 *
 * 通过 /actuator/health 暴露 PostgreSQL、Qdrant、Redis 的状态。
 * 各 HealthIndicator 仅在对应基础设施 Bean 存在时才注册。
 */
@Configuration
public class OfferCatcherHealthIndicators {

    private static final Logger log = LoggerFactory.getLogger(OfferCatcherHealthIndicators.class);

    @Bean("offercatcher-postgresql")
    @ConditionalOnBean(DataSource.class)
    HealthIndicator postgresHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(3);
                return valid ? Health.up().withDetail("database", conn.getCatalog()).build()
                    : Health.down().build();
            } catch (Exception e) {
                return Health.down(e).build();
            }
        };
    }

    @Bean("offercatcher-qdrant")
    @ConditionalOnBean(QdrantClient.class)
    HealthIndicator qdrantHealthIndicator(QdrantClient qdrantClient) {
        return () -> {
            try {
                QdrantOuterClass.HealthCheckReply reply = qdrantClient.healthCheckAsync().get();
                return Health.up()
                    .withDetail("title", reply.getTitle())
                    .withDetail("version", reply.getVersion())
                    .build();
            } catch (Exception e) {
                return Health.down(e).build();
            }
        };
    }

    @Bean("offercatcher-redis")
    @ConditionalOnBean(RedisConnectionFactory.class)
    HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return () -> {
            try {
                var conn = connectionFactory.getConnection();
                String pong = conn.ping();
                conn.close();
                return "PONG".equals(pong) ? Health.up().build() : Health.down().build();
            } catch (Exception e) {
                return Health.down(e).build();
            }
        };
    }
}
