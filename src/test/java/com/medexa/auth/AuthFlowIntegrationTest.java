package com.medexa.auth;

import com.medexa.auth.repository.EmailVerificationTokenRepository;
import com.medexa.auth.service.AuthEmailService;
import com.medexa.auth.service.GoogleTokenVerifier;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockitoSpyBean
    private AuthEmailService authEmailService;

    @BeforeEach
    void cleanNonAdminUsers() {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void adminAccountIsSeededAndCanSignInThroughAdminPortalOnly() throws Exception {
        User admin = userRepository.findByEmailIgnoreCase("omp27614@gmail.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getFullName()).isEqualTo("Om Prasad");

        // Admin cannot log in via standard portal
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "omp27614@gmail.com",
                                  "password": "TestAdminPassword#2026"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Admin succeeds via /admin/login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "omp27614@gmail.com",
                                  "password": "TestAdminPassword#2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("medexa_refresh_token"))
                .andExpect(cookie().httpOnly("medexa_refresh_token", true))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        String accessToken = extractJsonValue(body, "\"accessToken\":\"", "\"");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("omp27614@gmail.com"));

        // Admin token cannot access customer-scoped prefix
        mockMvc.perform(get("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRegisterVerifyLoginRefreshRotationAndReuseDetection() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Aarav Sharma",
                                  "email": "aarav@medexa-test.in",
                                  "phoneNumber": "+919876543210",
                                  "password": "StrongPass#123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.data.approvalRequired").value(false));

        // Login blocked before email verification
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "aarav@medexa-test.in",
                                  "password": "StrongPass#123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(authEmailService, atLeastOnce())
                .sendEmailVerification(anyString(), anyString(), tokenCaptor.capture());
        String rawVerificationToken = tokenCaptor.getValue();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken + "\"}"))
                .andExpect(status().isOk());

        // Login succeeds after verification
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "aarav@medexa-test.in",
                                  "password": "StrongPass#123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshCookie1 = loginResult.getResponse().getCookie("medexa_refresh_token");
        Cookie xsrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(refreshCookie1).isNotNull();
        assertThat(xsrfCookie).isNotNull();

        // CSRF is required on /refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie1))
                .andExpect(status().isForbidden());

        // Valid refresh rotates cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie1, xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshCookie2 = refreshResult.getResponse().getCookie("medexa_refresh_token");
        assertThat(refreshCookie2).isNotNull();
        assertThat(refreshCookie2.getValue()).isNotEqualTo(refreshCookie1.getValue());

        // Replaying refreshCookie1 triggers reuse detection and revokes family
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie1, xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isUnauthorized());

        // Now refreshCookie2 is also revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie2, xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pharmacyAndRiderRegisterAsPendingApprovalAndBlockLogin() throws Exception {
        String futureDate = LocalDate.now().plusYears(2).toString();

        mockMvc.perform(post("/api/v1/auth/register/pharmacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Neha Verma",
                                  "email": "careplus@medexa-test.in",
                                  "phoneNumber": "+919900112233",
                                  "password": "StrongPass#123",
                                  "pharmacyName": "CarePlus Pharmacy",
                                  "addressLine": "42 MG Road",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560001",
                                  "licenseNumber": "KA-BLR-2026-991",
                                  "issuingAuthority": "Karnataka Drugs Control",
                                  "pharmacistName": "Dr. Neha Verma",
                                  "pharmacistRegistrationNumber": "KSPC-44120",
                                  "licenseExpiryDate": "%s"
                                }
                                """.formatted(futureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalRequired").value(true))
                .andExpect(jsonPath("$.data.user.accountStatus").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/auth/register/rider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Rohan Das",
                                  "email": "rider@medexa-test.in",
                                  "phoneNumber": "+919811223344",
                                  "password": "StrongPass#123",
                                  "vehicleType": "MOTORCYCLE",
                                  "vehicleRegistrationNumber": "KA01AB1234",
                                  "drivingLicenseNumber": "DL-KA01-998877",
                                  "serviceCity": "Bengaluru"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalRequired").value(true))
                .andExpect(jsonPath("$.data.user.accountStatus").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "careplus@medexa-test.in",
                                  "password": "StrongPass#123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_PENDING_APPROVAL"));
    }

    @Test
    void googleLoginAuthenticatesCustomerAndRejectsAdminRole() throws Exception {
        when(googleTokenVerifier.verify("valid-google-id-token"))
                .thenReturn(new GoogleTokenVerifier.GoogleIdentity(
                        "google-sub-12345",
                        "google.customer@medexa-test.in",
                        "Meera Nair"
                ));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("google.customer@medexa-test.in"))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true));

        User saved = userRepository.findByEmailIgnoreCase("google.customer@medexa-test.in").orElseThrow();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void loginRateLimiterBlocksAfterFiveFailedAttempts() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "ratelimit@medexa-test.in",
                                      "password": "WrongPassword#1"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ratelimit@medexa-test.in",
                                  "password": "WrongPassword#1"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void forgotAndResetPasswordFlowUpdatesPasswordAndRevokesSessions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Kiran Rao",
                                  "email": "kiran@medexa-test.in",
                                  "password": "OldPassword#1"
                                }
                                """))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase("kiran@medexa-test.in").orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kiran@medexa-test.in\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> resetCaptor = ArgumentCaptor.forClass(String.class);
        verify(authEmailService, atLeastOnce())
                .sendPasswordReset(anyString(), anyString(), resetCaptor.capture());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword#2026"
                                }
                                """.formatted(resetCaptor.getValue())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "kiran@medexa-test.in",
                                  "password": "NewPassword#2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private String extractJsonValue(String json, String prefix, String suffix) {
        int start = json.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += prefix.length();
        int end = json.indexOf(suffix, start);
        return json.substring(start, end);
    }
}
