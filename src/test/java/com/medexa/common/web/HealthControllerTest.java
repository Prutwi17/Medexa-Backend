package com.medexa.common.web;

import com.medexa.common.exception.GlobalExceptionHandler;
import com.medexa.config.CorsConfig;
import com.medexa.config.CorsProperties;
import com.medexa.security.JwtAuthenticationFilter;
import com.medexa.security.JwtTokenService;
import com.medexa.security.RestAccessDeniedHandler;
import com.medexa.security.RestAuthenticationEntryPoint;
import com.medexa.security.SecurityConfig;
import com.medexa.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        CorsProperties.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void checkHealthReturnsStandardEnvelopeAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdContext.CORRELATION_ID_HEADER, "medexa-test-corr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdContext.CORRELATION_ID_HEADER, "medexa-test-corr-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("medexa-backend"))
                .andExpect(jsonPath("$.meta.correlationId").value("medexa-test-corr-123"));
    }

    @Test
    void invalidCorrelationIdHeaderIsReplacedWithGeneratedUuid() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdContext.CORRELATION_ID_HEADER, "bad\nheader"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdContext.CORRELATION_ID_HEADER))
                .andExpect(jsonPath("$.meta.correlationId").isNotEmpty());
    }
}
