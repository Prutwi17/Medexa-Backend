package com.medexa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldValidationError(
        String field,
        String message,
        Object rejectedValue
) {
}
