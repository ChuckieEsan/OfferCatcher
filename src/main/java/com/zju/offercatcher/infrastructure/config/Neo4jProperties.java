package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.neo4j")
public class Neo4jProperties {

    private String uri = "bolt://localhost:7687";
    private String user = "neo4j";
    private String password = "password";
    private String database = "neo4j";

}
