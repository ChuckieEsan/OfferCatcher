package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.qdrant")
public class QdrantProperties {

    private String host = "localhost";
    private int grpcPort = 6334;
    private int httpPort = 6333;
    private String collection = "questions";
    private String sessionSummaryCollection = "session_summaries";
    private int vectorSize = 1024;

}
