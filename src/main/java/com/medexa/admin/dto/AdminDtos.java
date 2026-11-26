package com.medexa.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PharmacyVerificationRequest(
            @NotNull(message = "Approval decision is required.")
            Boolean approved,

            @Size(max = 500)
            String notes
    ) {
    }

    public record PharmacyStatusUpdateRequest(
            @NotNull(message = "Suspension flag is required.")
            Boolean suspended,

            @Size(max = 500)
            String reason
    ) {
    }
}
