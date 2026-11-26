package com.medexa.customer.dto;

import com.medexa.customer.CustomerProfile;
import com.medexa.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record UpdateCustomerProfileRequest(
            @NotBlank(message = "Full name is required.")
            @Size(min = 2, max = 150)
            String fullName,

            @Size(max = 30)
            String phoneNumber,

            @Past(message = "Date of birth must be in the past.")
            LocalDate dateOfBirth,

            @Size(max = 30)
            String gender,

            @Size(max = 15)
            String bloodGroup,

            @Size(max = 500)
            String allergies,

            @Size(max = 500)
            String chronicConditions,

            @Size(max = 150)
            String emergencyContactName,

            @Size(max = 30)
            String emergencyContactPhone,

            @Size(max = 40)
            String preferredLanguage
    ) {
    }

    public record CustomerProfileResponse(
            Long userId,
            String fullName,
            String email,
            String phoneNumber,
            boolean emailVerified,
            LocalDate dateOfBirth,
            String gender,
            String bloodGroup,
            String allergies,
            String chronicConditions,
            String emergencyContactName,
            String emergencyContactPhone,
            String preferredLanguage,
            Instant updatedAt
    ) {
        public static CustomerProfileResponse from(User user, CustomerProfile profile) {
            return new CustomerProfileResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPhoneNumber(),
                    user.isEmailVerified(),
                    profile != null ? profile.getDateOfBirth() : null,
                    profile != null ? profile.getGender() : null,
                    profile != null ? profile.getBloodGroup() : null,
                    profile != null ? profile.getAllergies() : null,
                    profile != null ? profile.getChronicConditions() : null,
                    profile != null ? profile.getEmergencyContactName() : null,
                    profile != null ? profile.getEmergencyContactPhone() : null,
                    profile != null ? profile.getPreferredLanguage() : "English",
                    profile != null && profile.getUpdatedAt() != null ? profile.getUpdatedAt() : user.getUpdatedAt()
            );
        }
    }
}
