package com.medexa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMeta(
        Instant timestamp,
        String correlationId,
        String path,
        PageMeta pagination
) {
    public static ResponseMeta of(String correlationId, String path) {
        return new ResponseMeta(Instant.now(), correlationId, path, null);
    }

    public static ResponseMeta of(String correlationId, String path, PageMeta pagination) {
        return new ResponseMeta(Instant.now(), correlationId, path, pagination);
    }
}
