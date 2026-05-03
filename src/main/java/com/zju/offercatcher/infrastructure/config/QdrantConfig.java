package com.zju.offercatcher.infrastructure.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Bean
    public QdrantGrpcClient qdrantGrpcClient(QdrantProperties qdrant) {
        QdrantGrpcClient client = QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getGrpcPort(), false)
                .build();
        log.info("Qdrant gRPC client initialized: host={}, port={}", qdrant.getHost(), qdrant.getGrpcPort());
        return client;
    }

    @Bean
    public QdrantClient qdrantClient(QdrantGrpcClient grpcClient) {
        return new QdrantClient(grpcClient);
    }
}
