package com.medexa.invoice.service;

import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.invoice.Invoice;
import com.medexa.invoice.InvoiceRepository;
import com.medexa.invoice.dto.InvoiceDtos.InvoiceResponse;
import com.medexa.order.Order;
import com.medexa.order.OrderItem;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderRepository;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.service.PharmacyService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PharmacyService pharmacyService;
    private final PdfInvoiceGenerator pdfGenerator;

    public record RenderedInvoicePdf(String filename, byte[] bytes) {
    }

    @Transactional
    public InvoiceResponse getCustomerInvoice(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        Invoice invoice = ensureInvoiceIssued(order);
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public RenderedInvoicePdf downloadCustomerInvoicePdf(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return buildPdfPayload(order);
    }

    @Transactional
    public RenderedInvoicePdf downloadPharmacyInvoicePdf(Long pharmacyUserId, Long orderId) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(pharmacyUserId);
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy order not found."));
        return buildPdfPayload(order);
    }

    @Transactional
    public RenderedInvoicePdf downloadAdminInvoicePdf(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return buildPdfPayload(order);
    }

    @Transactional
    public Invoice ensureInvoiceIssued(Order order) {
        if (order.getPaymentStatus() != OrderPaymentStatus.PAID
                && order.getPaymentStatus() != OrderPaymentStatus.REFUND_REQUESTED
                && order.getPaymentStatus() != OrderPaymentStatus.REFUNDED) {
            throw new BusinessRuleException("Invoices are only issued for verified paid orders.");
        }

        return invoiceRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    Pharmacy pharmacy = order.getPharmacy();
                    boolean taxApplicable = order.getTaxAmount() != null
                            && order.getTaxAmount().compareTo(BigDecimal.ZERO) > 0;
                    String taxNote = taxApplicable
                            ? "Applicable marketplace tax included on taxable medicine total. Official GST tax registration remains with the dispensing pharmacy where applicable."
                            : "No tax charged on this order.";

                    String invoiceNumber = "INV-" + Year.now().getValue() + "-"
                            + String.format("%06d", order.getId());

                    String pharmAddr = pharmacy.getAddressLine() + ", " + pharmacy.getCity() + ", "
                            + pharmacy.getState() + " - " + pharmacy.getPostalCode();
                    String custAddr = order.getShippingAddress() + ", " + order.getShippingCity() + ", "
                            + order.getShippingState() + " - " + order.getShippingPostalCode();

                    Invoice invoice = Invoice.builder()
                            .invoiceNumber(invoiceNumber)
                            .order(order)
                            .customer(order.getCustomer())
                            .pharmacy(pharmacy)
                            .pharmacyName(pharmacy.getPharmacyName())
                            .pharmacyAddress(pharmAddr)
                            .customerName(order.getShippingRecipientName())
                            .customerAddress(custAddr)
                            .itemsSubtotal(order.getItemsSubtotal())
                            .discountAmount(order.getDiscountAmount())
                            .deliveryFee(order.getDeliveryFee())
                            .taxAmount(order.getTaxAmount())
                            .grandTotal(order.getGrandTotal())
                            .taxApplicable(taxApplicable)
                            .taxNote(taxNote)
                            .paymentReference(order.getCheckoutSession().getPaymentReference())
                            .paymentStatus(order.getPaymentStatus().name())
                            .issuedAt(Instant.now())
                            .build();
                    return invoiceRepository.save(invoice);
                });
    }

    private RenderedInvoicePdf buildPdfPayload(Order order) {
        Invoice invoice = ensureInvoiceIssued(order);
        if (!invoice.getPaymentStatus().equals(order.getPaymentStatus().name())
                || (invoice.getPaymentReference() == null
                    && order.getCheckoutSession().getPaymentReference() != null)) {
            invoice.setPaymentStatus(order.getPaymentStatus().name());
            invoice.setPaymentReference(order.getCheckoutSession().getPaymentReference());
            invoiceRepository.save(invoice);
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdWithRefs(order.getId());
        byte[] bytes = pdfGenerator.render(invoice, items);
        return new RenderedInvoicePdf(invoice.getInvoiceNumber() + ".pdf", bytes);
    }
}
