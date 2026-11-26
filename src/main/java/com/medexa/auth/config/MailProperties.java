package com.medexa.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private boolean enabled = false;
    private String fromAddress = "no-reply@medexa.in";
}
