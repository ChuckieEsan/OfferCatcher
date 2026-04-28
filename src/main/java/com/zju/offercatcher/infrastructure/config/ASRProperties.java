package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.asr")
public class ASRProperties {

    private String appId = "";
    private String apiKey = "";
    private String apiSecret = "";

}
