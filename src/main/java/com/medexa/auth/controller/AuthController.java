package com.medexa.auth.controller;

import com.medexa.auth.dto.AuthTokenResponse;
import com.medexa.auth.dto.CustomerRegisterRequest;
import com.medexa.auth.dto.ForgotPasswordRequest;
import com.medexa.auth.dto.GoogleLoginRequest;
import com.medexa.auth.dto.LoginRequest;
import com.medexa.auth.dto.PharmacyRegisterRequest;
import com.medexa.auth.dto.RegistrationResponse;
import com.medexa.auth.dto.ResendVerificationRequest;
import com.medexa.auth.dto.ResetPasswordRequest;
import com.medexa.auth.dto.RiderRegisterRequest;
import com.medexa.auth.dto.VerifyEmailRequest;
import com.medexa.auth.service.AuthService;
import com.medexa.common.api.ApiResponse;
import com.medexa.common.web.ClientIpResolver;
import com.medexa.config.OpenApiConfig;
import com.medexa.security.MedexaUserPrincipal;
import com.medexa.user.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, Google OAuth, token rotation, and account recovery")
public class AuthController {

    private final AuthService authService;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    @GetMapping("/csrf")
    @Operation(summary = "Initialize CSRF token cookie", description = "Materializes the XSRF-TOKEN cookie for SPA refresh/logout requests.")
    public ApiResponse<Map<String, String>> csrfToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        CsrfToken token = ensureCsrfCookie(request, response);
        return ApiResponse.ok("CSRF token initialized", Map.of("headerName", token.getHeaderName()));
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account")
    public ApiResponse<RegistrationResponse> registerCustomer(@Valid @RequestBody CustomerRegisterRequest request) {
        RegistrationResponse response = authService.registerCustomer(request);
        return ApiResponse.ok(
                "Customer account created. Please check your email to verify your address.",
                response
        );
    }

    @PostMapping("/register/pharmacy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a pharmacy partner with pending verification status")
    public ApiResponse<RegistrationResponse> registerPharmacy(@Valid @RequestBody PharmacyRegisterRequest request) {
        RegistrationResponse response = authService.registerPharmacy(request);
        return ApiResponse.ok(
                "Pharmacy registration submitted. Your drug license is pending administrator verification.",
                response
        );
    }

    @PostMapping("/register/rider")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a delivery rider with pending verification status")
    public ApiResponse<RegistrationResponse> registerRider(@Valid @RequestBody RiderRegisterRequest request) {
        RegistrationResponse response = authService.registerRider(request);
        return ApiResponse.ok(
                "Rider registration submitted. Your profile is pending administrator verification.",
                response
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate Customer, Pharmacy, or Rider with email and password")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureCsrfCookie(httpRequest, httpResponse);
        AuthTokenResponse tokens = authService.loginUser(
                request,
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpResponse
        );
        return ApiResponse.ok("Signed in successfully.", tokens);
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Authenticate seeded Administrator account")
    public ApiResponse<AuthTokenResponse> adminLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureCsrfCookie(httpRequest, httpResponse);
        AuthTokenResponse tokens = authService.loginAdmin(
                request,
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpResponse
        );
        return ApiResponse.ok("Administrator signed in successfully.", tokens);
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate Customer, Pharmacy, or Rider with a verified Google ID token")
    public ApiResponse<AuthTokenResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        ensureCsrfCookie(httpRequest, httpResponse);
        AuthTokenResponse tokens = authService.loginWithGoogle(
                request,
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpResponse
        );
        return ApiResponse.ok("Signed in with Google successfully.", tokens);
    }

    private CsrfToken ensureCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken existing = csrfTokenRepository.loadToken(request);
        if (existing != null) {
            return existing;
        }
        CsrfToken generated = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(generated, request, response);
        return generated;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token cookie and issue a fresh JWT access token")
    public ApiResponse<AuthTokenResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthTokenResponse tokens = authService.refreshSession(
                httpRequest,
                httpResponse,
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return ApiResponse.ok("Session refreshed.", tokens);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out current session and revoke refresh token")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ApiResponse.ok("Logged out successfully.", null);
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Revoke all refresh tokens across all devices for the authenticated user")
    public ApiResponse<Void> logoutAll(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            HttpServletResponse httpResponse) {
        authService.logoutAll(principal.getUserId(), httpResponse);
        return ApiResponse.ok("All active sessions revoked.", null);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify account email address using verification token")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.ok("Email address verified successfully.", null);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ApiResponse.ok(
                "If an unverified account exists for that email, a fresh verification link has been sent.",
                null
        );
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.forgotPassword(request, ClientIpResolver.resolve(httpRequest));
        return ApiResponse.ok(
                "If an account exists for that email, a password reset link has been sent.",
                null
        );
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a valid reset token")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok("Password reset successfully. You may now sign in.", null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Get currently authenticated user profile")
    public ApiResponse<UserSummaryResponse> me(@AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok("Authenticated user fetched.", authService.getCurrentUser(principal.getUserId()));
    }
}
