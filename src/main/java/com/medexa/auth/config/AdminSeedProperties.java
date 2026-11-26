package com.medexa.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin.seed")
public class AdminSeedProperties {

    private boolean enabled = true;
    private String fullName = "Om Prasad";
    private String email = "omp27614@gmail.com";
    private String password = "";
}
