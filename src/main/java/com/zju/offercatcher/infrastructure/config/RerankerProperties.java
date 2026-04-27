package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.reranker")
public class RerankerProperties {

    private String modelPath = "models/bge-reranker-base";
    private int maxLength = 512;

}
