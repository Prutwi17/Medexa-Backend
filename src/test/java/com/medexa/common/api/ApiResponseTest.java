package com.medexa.common.api;

import com.medexa.common.web.CorrelationIdContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @AfterEach
    void cleanup() {
        CorrelationIdContext.clear();
    }

    @Test
    void pagedResponsePopulatesPaginationMetadata() {
        CorrelationIdContext.set("corr-page-001");
        Page<String> page = new PageImpl<>(List.of("Amoxicillin", "Paracetamol"), PageRequest.of(0, 2), 5);

        ApiResponse<List<String>> response = ApiResponse.paged("Catalog fetched", page);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly("Amoxicillin", "Paracetamol");
        assertThat(response.meta().correlationId()).isEqualTo("corr-page-001");
        assertThat(response.meta().pagination().totalElements()).isEqualTo(5);
        assertThat(response.meta().pagination().totalPages()).isEqualTo(3);
        assertThat(response.meta().pagination().hasNext()).isTrue();
    }
}
