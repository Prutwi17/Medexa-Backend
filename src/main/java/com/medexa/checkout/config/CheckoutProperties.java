package com.medexa.checkout.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Getter
@Setter
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    private int reservationTtlMinutes = 15;
    private BigDecimal taxRatePercent = new BigDecimal("5.00");
    private BigDecimal platformCommissionPercent = new BigDecimal("10.00");
}
