package com.medexa.auth.service;

import com.medexa.auth.config.AuthProperties;
import com.medexa.common.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginRateLimiterService {

    private final AuthProperties authProperties;
    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public void assertAllowed(String email, String ipAddress) {
        String key = buildKey(email, ipAddress);
        Instant now = Instant.now();
        Duration window = Duration.ofMinutes(authProperties.getRateLimit().getWindowMinutes());
        int maxAttempts = authProperties.getRateLimit().getMaxAttempts();

        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            pruneExpired(timestamps, now, window);
            if (timestamps.size() >= maxAttempts) {
                Instant oldest = timestamps.peekFirst();
                long retryAfterSeconds = oldest != null
                        ? Math.max(1L, Duration.between(now, oldest.plus(window)).getSeconds())
                        : window.getSeconds();
                throw new RateLimitExceededException(
                        "Too many failed login attempts. Please wait before trying again.",
                        retryAfterSeconds
                );
            }
        }
    }

    public void recordFailure(String email, String ipAddress) {
        String key = buildKey(email, ipAddress);
        Instant now = Instant.now();
        Duration window = Duration.ofMinutes(authProperties.getRateLimit().getWindowMinutes());

        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            pruneExpired(timestamps, now, window);
            timestamps.addLast(now);
        }
    }

    public void reset(String email, String ipAddress) {
        attemptsByKey.remove(buildKey(email, ipAddress));
    }

    private void pruneExpired(Deque<Instant> timestamps, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }
    }

    private String buildKey(String email, String ipAddress) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String normalizedIp = ipAddress == null ? "unknown" : ipAddress.trim();
        return normalizedEmail + "|" + normalizedIp;
    }
}
