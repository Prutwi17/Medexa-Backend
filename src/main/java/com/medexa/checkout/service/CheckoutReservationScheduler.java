package com.medexa.checkout.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutReservationScheduler {

    private final CheckoutService checkoutService;

    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredCheckoutReservations() {
        int expiredCount = checkoutService.expireTimedOutCheckouts();
        if (expiredCount > 0) {
            log.info("Released stock reservations for {} expired checkout session(s).", expiredCount);
        }
    }
}
