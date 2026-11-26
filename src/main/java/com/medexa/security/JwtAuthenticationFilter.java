package com.medexa.security;

import com.medexa.common.exception.UnauthorizedException;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (rawToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtTokenService.DecodedAccessToken decoded = jwtTokenService.parseAndValidateAccessToken(rawToken);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findById(decoded.userId())
                        .orElseThrow(() -> new UnauthorizedException("User account no longer exists."));
                if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                    throw new UnauthorizedException("Account is not active.");
                }

                MedexaUserPrincipal principal = new MedexaUserPrincipal(user);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute("medexa.auth.error", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
