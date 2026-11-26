package com.medexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email address is required.")
        @Email(message = "Provide a valid email address.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 1, max = 128, message = "Password cannot exceed 128 characters.")
        String password
) {
}
