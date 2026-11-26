package com.medexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email address is required.")
        @Email(message = "Provide a valid email address.")
        String email
) {
}
