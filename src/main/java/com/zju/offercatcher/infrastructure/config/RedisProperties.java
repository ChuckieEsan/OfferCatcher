package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.redis")
public class RedisProperties {

    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private int ttl = 86400;

}
