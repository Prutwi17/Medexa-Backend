package com.medexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Email address is required.")
        @Email(message = "Provide a valid email address.")
        @Size(max = 190, message = "Email address cannot exceed 190 characters.")
        String email,

        @Pattern(regexp = "^[+0-9\\s-]{7,20}$", message = "Provide a valid phone number.")
        String phoneNumber,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must include uppercase, lowercase, a number, and a special character."
        )
        String password
) {
}
