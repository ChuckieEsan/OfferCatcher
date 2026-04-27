package com.zju.offercatcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.zju.offercatcher.infrastructure.config")
public class OfferCatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferCatcherApplication.class, args);
    }

}
