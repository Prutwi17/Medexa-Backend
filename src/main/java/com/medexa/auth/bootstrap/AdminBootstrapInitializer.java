package com.medexa.auth.bootstrap;

import com.medexa.auth.config.AdminSeedProperties;
import com.medexa.user.AccountStatus;
import com.medexa.user.AuthProvider;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminSeedProperties adminSeedProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminSeedProperties.isEnabled()) {
            return;
        }

        String email = adminSeedProperties.getEmail() != null
                ? adminSeedProperties.getEmail().trim().toLowerCase()
                : "";
        if (email.isEmpty()) {
            log.warn("Admin seeding enabled but ADMIN_SEED_EMAIL is blank; skipping.");
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        String rawPassword = adminSeedProperties.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("Admin account ({}) does not exist and ADMIN_SEED_PASSWORD is not set; skipping automatic seed.",
                    email);
            return;
        }

        User admin = User.builder()
                .fullName(adminSeedProperties.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();

        userRepository.save(admin);
        log.info("Seeded initial Medexa ADMIN account for {}", email);
    }
}
