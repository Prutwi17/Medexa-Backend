package com.medexa.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationIdContext.CORRELATION_ID_HEADER);
        String correlationId = isValid(incoming) ? incoming : UUID.randomUUID().toString();

        CorrelationIdContext.set(correlationId);
        response.setHeader(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdContext.clear();
        }
    }

    private boolean isValid(String candidate) {
        return candidate != null && VALID_CORRELATION_ID.matcher(candidate).matches();
    }
}
