package com.medexa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medexa.common.web.CorrelationIdContext;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ApiError error,
        ResponseMeta meta
) {
    public static <T> ApiResponse<T> ok(T data) {
        return ok("Request processed successfully", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                ResponseMeta.of(CorrelationIdContext.get(), null)
        );
    }

    public static <T> ApiResponse<List<T>> paged(String message, Page<T> page) {
        return new ApiResponse<>(
                true,
                message,
                page.getContent(),
                null,
                ResponseMeta.of(CorrelationIdContext.get(), null, PageMeta.from(page))
        );
    }

    public static ApiResponse<Void> error(ApiError error, String path) {
        return new ApiResponse<>(
                false,
                error.message(),
                null,
                error,
                ResponseMeta.of(CorrelationIdContext.get(), path)
        );
    }
}
