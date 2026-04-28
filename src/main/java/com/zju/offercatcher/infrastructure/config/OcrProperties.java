package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.ocr")
public class OcrProperties {

    private String serviceUrl = "http://localhost:8001";
    private int connectTimeout = 30;
    private int readTimeout = 60;

}
