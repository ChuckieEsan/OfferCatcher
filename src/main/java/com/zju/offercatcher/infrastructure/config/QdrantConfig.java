package com.zju.offercatcher.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant 配置类
 *
 * 注意：目前为简化版本，后续集成时添加 QdrantClient Bean。
 */
@Configuration
public class QdrantConfig {

    @Bean
    @ConfigurationProperties(prefix = "offercatcher.qdrant")
    public QdrantProperties qdrantProperties() {
        return new QdrantProperties();
    }

    /**
     * Qdrant 连接配置属性
     */
    public static class QdrantProperties {
        private String host = "localhost";
        private int grpcPort = 6334;
        private int httpPort = 6333;
        private String collection = "questions";
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

        public int getVectorSize() {
            return vectorSize;
        }

        public void setVectorSize(int vectorSize) {
            this.vectorSize = vectorSize;
        }
    }
}