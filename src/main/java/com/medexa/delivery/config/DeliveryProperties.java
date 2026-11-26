package com.medexa.delivery.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryProperties {

    private int acceptanceTimeoutSeconds = 120;
    private int otpExpiryMinutes = 15;
    private int maxOtpAttempts = 3;
    private BigDecimal defaultBaseFee = new BigDecimal("40.00");
    private BigDecimal perKmFee = new BigDecimal("8.00");
    private double maxDistanceKm = 15.0;
    private double distanceWeight = 1.0;
    private double workloadWeight = 2.5;
    private long timeoutSweepIntervalMs = 30000;
}
