package com.zju.offercatcher.infrastructure.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant 配置类
 *
 * 管理 Qdrant 连接（gRPC Client）、集合名称和向量参数。
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "offercatcher.qdrant")
    public QdrantProperties qdrantProperties() {
        return new QdrantProperties();
    }

    @Bean
    public QdrantGrpcClient qdrantGrpcClient(QdrantProperties properties) {
        QdrantGrpcClient client = QdrantGrpcClient.newBuilder(properties.getHost(), properties.getGrpcPort(), false)
            .build();
        log.info("Qdrant gRPC client initialized: host={}, port={}", properties.getHost(), properties.getGrpcPort());
        return client;
    }

    @Bean
    public QdrantClient qdrantClient(QdrantGrpcClient grpcClient) {
        return new QdrantClient(grpcClient);
    }

    /**
     * Qdrant 连接配置属性
     */
    public static class QdrantProperties {
        private String host = "localhost";
        private int grpcPort = 6334;
        private int httpPort = 6333;
        private String collection = "questions";
        private String sessionSummaryCollection = "session_summaries";
        private int vectorSize = 1024;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getGrpcPort() {
            return grpcPort;
        }

        public void setGrpcPort(int grpcPort) {
            this.grpcPort = grpcPort;
        }

        public int getHttpPort() {
            return httpPort;
        }

        public void setHttpPort(int httpPort) {
            this.httpPort = httpPort;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getSessionSummaryCollection() {
            return sessionSummaryCollection;
        }

        public void setSessionSummaryCollection(String sessionSummaryCollection) {
            this.sessionSummaryCollection = sessionSummaryCollection;
        }

        public int getVectorSize() {
            return vectorSize;
        }

        public void setVectorSize(int vectorSize) {
            this.vectorSize = vectorSize;
        }
    }
}
