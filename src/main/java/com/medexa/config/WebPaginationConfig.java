package com.medexa.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
@RequiredArgsConstructor
public class WebPaginationConfig {

    private final PaginationProperties paginationProperties;

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setFallbackPageable(PageRequest.of(0, paginationProperties.getDefaultPageSize()));
            resolver.setMaxPageSize(paginationProperties.getMaxPageSize());
            resolver.setOneIndexedParameters(false);
        };
    }
}
