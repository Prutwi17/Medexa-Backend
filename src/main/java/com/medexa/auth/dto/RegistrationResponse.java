package com.medexa.auth.dto;

import com.medexa.user.UserSummaryResponse;

public record RegistrationResponse(
        UserSummaryResponse user,
        boolean emailVerificationRequired,
        boolean approvalRequired
) {
}
