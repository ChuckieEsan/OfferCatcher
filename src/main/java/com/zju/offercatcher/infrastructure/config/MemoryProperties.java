package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.memory")
public class MemoryProperties {

    private int retrievalMinLength = 10;
    private int topK = 5;
    private int contextMaxSize = 20480;

}
