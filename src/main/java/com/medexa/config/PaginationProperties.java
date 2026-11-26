package com.medexa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.pagination")
public class PaginationProperties {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
}
