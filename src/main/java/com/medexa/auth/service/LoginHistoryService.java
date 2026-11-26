package com.medexa.auth.service;

import com.medexa.auth.entity.AuthMethod;
import com.medexa.auth.entity.LoginHistory;
import com.medexa.auth.entity.LoginOutcome;
import com.medexa.auth.repository.LoginHistoryRepository;
import com.medexa.common.web.CorrelationIdContext;
import com.medexa.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(
            User user,
            String emailAttempted,
            AuthMethod authMethod,
            String ipAddress,
            String userAgent) {
        saveEntry(user, emailAttempted, authMethod, LoginOutcome.SUCCESS, null, ipAddress, userAgent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            User user,
            String emailAttempted,
            AuthMethod authMethod,
            LoginOutcome outcome,
            String failureReason,
            String ipAddress,
            String userAgent) {
        saveEntry(user, emailAttempted, authMethod, outcome, failureReason, ipAddress, userAgent);
    }

    private void saveEntry(
            User user,
            String emailAttempted,
            AuthMethod authMethod,
            LoginOutcome outcome,
            String failureReason,
            String ipAddress,
            String userAgent) {
        String truncatedAgent = userAgent != null && userAgent.length() > 512
                ? userAgent.substring(0, 512)
                : userAgent;

        LoginHistory entry = LoginHistory.builder()
                .user(user)
                .emailAttempted(emailAttempted != null ? emailAttempted.trim().toLowerCase() : "unknown")
                .authMethod(authMethod)
                .outcome(outcome)
                .failureReason(failureReason)
                .ipAddress(ipAddress)
                .userAgent(truncatedAgent)
                .correlationId(CorrelationIdContext.get())
                .build();

        loginHistoryRepository.save(entry);
    }
}
