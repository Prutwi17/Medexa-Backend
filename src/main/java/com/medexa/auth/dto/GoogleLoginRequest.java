package com.medexa.auth.dto;

import com.medexa.user.UserRole;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required.")
        String idToken,

        UserRole role
) {
}
