package com.medexa.checkout.service;

import com.medexa.checkout.Coupon;
import com.medexa.checkout.CouponRepository;
import com.medexa.checkout.DiscountType;
import com.medexa.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public record CouponEvaluation(
            Coupon coupon,
            String appliedCode,
            BigDecimal discountAmount,
            String message
    ) {
    }

    @Transactional(readOnly = true)
    public CouponEvaluation evaluate(String rawCode, BigDecimal itemsSubtotal, boolean throwOnInvalid) {
        if (rawCode == null || rawCode.isBlank()) {
            return new CouponEvaluation(null, null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), null);
        }

        String code = rawCode.trim().toUpperCase();
        Optional<Coupon> opt = couponRepository.findByCodeIgnoreCase(code);
        if (opt.isEmpty()) {
            return handleInvalid("Coupon code '" + code + "' was not found.", throwOnInvalid);
        }

        Coupon coupon = opt.get();
        Instant now = Instant.now();
        if (!coupon.isActive()) {
            return handleInvalid("Coupon '" + code + "' is currently inactive.", throwOnInvalid);
        }
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
            return handleInvalid("Coupon '" + code + "' is expired or not yet active.", throwOnInvalid);
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return handleInvalid("Coupon '" + code + "' has reached its redemption limit.", throwOnInvalid);
        }
        if (itemsSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return handleInvalid(
                    "Minimum medicine subtotal of ₹" + coupon.getMinOrderAmount().toPlainString()
                            + " is required for coupon '" + code + "'.",
                    throwOnInvalid
            );
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = itemsSubtotal
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        }

        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount().setScale(2, RoundingMode.HALF_UP);
        }
        if (discount.compareTo(itemsSubtotal) > 0) {
            discount = itemsSubtotal.setScale(2, RoundingMode.HALF_UP);
        }

        return new CouponEvaluation(coupon, coupon.getCode(), discount, coupon.getDescription());
    }

    @Transactional
    public void incrementUsage(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        couponRepository.findByCodeIgnoreCase(code.trim()).ifPresent(coupon -> {
            coupon.setUsedCount((coupon.getUsedCount() != null ? coupon.getUsedCount() : 0) + 1);
            couponRepository.save(coupon);
        });
    }

    private CouponEvaluation handleInvalid(String reason, boolean throwOnInvalid) {
        if (throwOnInvalid) {
            throw new BusinessRuleException(reason);
        }
        return new CouponEvaluation(null, null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), reason);
    }
}
