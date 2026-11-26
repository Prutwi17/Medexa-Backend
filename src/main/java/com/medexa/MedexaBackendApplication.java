package com.medexa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.medexa")
public class MedexaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedexaBackendApplication.class, args);
    }
}
