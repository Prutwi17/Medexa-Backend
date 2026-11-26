package com.medexa.user;

import java.time.Instant;

public record UserSummaryResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        UserRole role,
        AccountStatus accountStatus,
        boolean emailVerified,
        AuthProvider authProvider,
        Instant lastLoginAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getAccountStatus(),
                user.isEmailVerified(),
                user.getAuthProvider(),
                user.getLastLoginAt()
        );
    }
}
