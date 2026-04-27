package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.embedding")
public class EmbeddingProperties {

    private String modelPath = "models/bge-m3";
    private int vectorSize = 1024;

}
