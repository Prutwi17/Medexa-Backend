package com.medexa.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String uploadDir = "./uploads";
    private long maxFileSizeBytes = 10_485_760L;
}
