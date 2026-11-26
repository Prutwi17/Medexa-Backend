package com.medexa.invoice.dto;

import com.medexa.invoice.Invoice;
import java.math.BigDecimal;
import java.time.Instant;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record InvoiceResponse(
            Long id,
            String invoiceNumber,
            Long orderId,
            String orderNumber,
            String checkoutReference,
            String pharmacyName,
            String pharmacyAddress,
            String customerName,
            String customerAddress,
            BigDecimal itemsSubtotal,
            BigDecimal discountAmount,
            BigDecimal deliveryFee,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            boolean taxApplicable,
            String taxNote,
            String paymentReference,
            String paymentStatus,
            Instant issuedAt
    ) {
        public static InvoiceResponse from(Invoice inv) {
            return new InvoiceResponse(
                    inv.getId(),
                    inv.getInvoiceNumber(),
                    inv.getOrder().getId(),
                    inv.getOrder().getOrderNumber(),
                    inv.getOrder().getCheckoutSession().getCheckoutReference(),
                    inv.getPharmacyName(),
                    inv.getPharmacyAddress(),
                    inv.getCustomerName(),
                    inv.getCustomerAddress(),
                    inv.getItemsSubtotal(),
                    inv.getDiscountAmount(),
                    inv.getDeliveryFee(),
                    inv.getTaxAmount(),
                    inv.getGrandTotal(),
                    inv.isTaxApplicable(),
                    inv.getTaxNote(),
                    inv.getPaymentReference(),
                    inv.getPaymentStatus(),
                    inv.getIssuedAt()
            );
        }
    }
}
