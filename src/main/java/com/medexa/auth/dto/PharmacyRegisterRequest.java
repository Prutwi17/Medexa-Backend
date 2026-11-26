package com.medexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PharmacyRegisterRequest(
        @NotBlank(message = "Owner or contact full name is required.")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Email address is required.")
        @Email(message = "Provide a valid email address.")
        @Size(max = 190, message = "Email address cannot exceed 190 characters.")
        String email,

        @NotBlank(message = "Contact phone number is required.")
        @Pattern(regexp = "^[+0-9\\s-]{7,20}$", message = "Provide a valid phone number.")
        String phoneNumber,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must include uppercase, lowercase, a number, and a special character."
        )
        String password,

        @NotBlank(message = "Pharmacy legal name is required.")
        @Size(min = 2, max = 200, message = "Pharmacy name must be between 2 and 200 characters.")
        String pharmacyName,

        @NotBlank(message = "Street address is required.")
        @Size(max = 300, message = "Address cannot exceed 300 characters.")
        String addressLine,

        @NotBlank(message = "City is required.")
        @Size(max = 100, message = "City cannot exceed 100 characters.")
        String city,

        @NotBlank(message = "State is required.")
        @Size(max = 100, message = "State cannot exceed 100 characters.")
        String state,

        @NotBlank(message = "Postal code is required.")
        @Size(max = 20, message = "Postal code cannot exceed 20 characters.")
        String postalCode,

        @NotBlank(message = "Drug license number is required.")
        @Size(max = 120, message = "License number cannot exceed 120 characters.")
        String licenseNumber,

        @NotBlank(message = "Issuing authority is required.")
        @Size(max = 180, message = "Issuing authority cannot exceed 180 characters.")
        String issuingAuthority,

        @NotBlank(message = "Registered pharmacist name is required.")
        @Size(max = 150, message = "Pharmacist name cannot exceed 150 characters.")
        String pharmacistName,

        @NotBlank(message = "Pharmacist registration number is required.")
        @Size(max = 120, message = "Pharmacist registration number cannot exceed 120 characters.")
        String pharmacistRegistrationNumber,

        @NotNull(message = "License expiry date is required.")
        @Future(message = "Drug license expiry date must be in the future.")
        LocalDate licenseExpiryDate,

        @Size(max = 500, message = "Document URL cannot exceed 500 characters.")
        String licenseDocumentUrl
) {
}
