package com.medexa.auth.dto;

import com.medexa.rider.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RiderRegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Email address is required.")
        @Email(message = "Provide a valid email address.")
        @Size(max = 190, message = "Email address cannot exceed 190 characters.")
        String email,

        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^[+0-9\\s-]{7,20}$", message = "Provide a valid phone number.")
        String phoneNumber,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must include uppercase, lowercase, a number, and a special character."
        )
        String password,

        @NotNull(message = "Vehicle type is required.")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle registration number is required.")
        @Size(max = 60, message = "Vehicle registration number cannot exceed 60 characters.")
        String vehicleRegistrationNumber,

        @NotBlank(message = "Driving license number is required.")
        @Size(max = 100, message = "Driving license number cannot exceed 100 characters.")
        String drivingLicenseNumber,

        @NotBlank(message = "Service city is required.")
        @Size(max = 100, message = "Service city cannot exceed 100 characters.")
        String serviceCity
) {
}
