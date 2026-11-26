package com.medexa.payment;

import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItemRepository;
import com.medexa.category.CategoryRepository;
import com.medexa.checkout.CheckoutSessionRepository;
import com.medexa.checkout.CheckoutStatus;
import com.medexa.checkout.service.RazorpaySignatureUtils;
import com.medexa.customer.CustomerProfileRepository;
import com.medexa.customer.RecentlyViewedMedicineRepository;
import com.medexa.inventory.MedicineBatch;
import com.medexa.inventory.MedicineBatchRepository;
import com.medexa.invoice.InvoiceRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.order.RefundStatus;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.prescription.PrescriptionOrderLinkRepository;
import com.medexa.prescription.PrescriptionRepository;
import com.medexa.refund.RefundRecordRepository;
import com.medexa.refund.RefundRecordStatus;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import com.medexa.wishlist.WishlistItemRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookRefundAndInvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository medicineBatchRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionOrderLinkRepository prescriptionOrderLinkRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private RecentlyViewedMedicineRepository recentlyViewedRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RefundRecordRepository refundRecordRepository;

    @Autowired
    private RazorpayWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.webhook-secret}")
    private String razorpayWebhookSecret;

    @BeforeEach
    @AfterEach
    void cleanup() {
        invoiceRepository.deleteAll();
        refundRecordRepository.deleteAll();
        webhookEventRepository.deleteAll();
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderStatusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        checkoutSessionRepository.deleteAll();
        cartItemRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        recentlyViewedRepository.deleteAll();
        prescriptionOrderLinkRepository.deleteAll();
        prescriptionRepository.deleteAll();
        addressRepository.deleteAll();
        customerProfileRepository.deleteAll();
        medicineRepository.deleteAll();
        categoryRepository.deleteAll();
        pharmacyRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void webhooksOutOfOrderDeduplicationInvoicesAndRefundsSyncEndToEnd() throws Exception {
        String adminToken = signInAdmin();

        MvcResult catRes = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Phase6 Pain Relief",
                                  "description": "Analgesics and fever care",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = extractLong(catRes.getResponse().getContentAsString(), "\"id\":");

        registerAndApprovePharmacy(adminToken, "p6pharma@medexa-test.in", "HealthFirst Pharmacy", "LIC-P6-101");
        String pharmaToken = signInUser("p6pharma@medexa-test.in", "StrongPass#123");
        addDeliveryZone(pharmaToken, "Koramangala", "560034", "35.00");

        Long medId = createAndApproveMedicine(
                adminToken, pharmaToken, categoryId, "Paracetamol 650", "OTC", false, "50.00", "BATCH-P6-1", 20
        );

        registerAndActivateCustomer("p6buyer@medexa-test.in", "Ananya Sharma");
        registerAndActivateCustomer("p6other@medexa-test.in", "Vikram Nair");
        String buyerToken = signInUser("p6buyer@medexa-test.in", "StrongPass#123");
        String otherToken = signInUser("p6other@medexa-test.in", "StrongPass#123");

        Long addressId = addCustomerAddress(buyerToken, "560034");

        // Add 2 units @ 50.00 = 100.00
        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicineId\":" + medId + ",\"quantity\":2}"))
                .andExpect(status().isOk());

        // 1. Create checkout session -> internal Payment CREATED
        MvcResult sessionRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.data.razorpayKeyId").value("rzp_test_medexa_local"))
                .andReturn();

        String sessionJson = sessionRes.getResponse().getContentAsString();
        String checkoutRef = extractString(sessionJson, "\"checkoutReference\":\"", "\"");
        String gatewayOrderId = extractString(sessionJson, "\"razorpayOrderId\":\"", "\"");
        Long amountPaise = extractLong(sessionJson, "\"amountInPaise\":");
        int ordersStart = sessionJson.indexOf("\"orders\":");
        Long orderId = extractLong(sessionJson.substring(ordersStart), "\"id\":");

        Payment internalPayment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
        assertThat(internalPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);

        // 2. Forged signature rejected cleanly and checkout remains AWAITING_PAYMENT
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayOrderId": "%s",
                                  "razorpayPaymentId": "pay_forged_123",
                                  "razorpaySignature": "forged_hmac_hex",
                                  "idempotencyKey": "forged-key-1"
                                }
                                """.formatted(gatewayOrderId)))
                .andExpect(status().is4xxClientError());
        assertThat(checkoutSessionRepository.findByCheckoutReference(checkoutRef).orElseThrow().getStatus())
                .isEqualTo(CheckoutStatus.AWAITING_PAYMENT);

        // 3. Invalid webhook signature rejected
        String authPayload = """
                {"event":"payment.authorized","id":"evt_auth_p6_001","payload":{"payment":{"entity":{"id":"pay_p6_live_001","order_id":"%s","amount":%d,"status":"authorized"}}}}
                """.formatted(gatewayOrderId, amountPaise).trim();

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", "tampered_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authPayload))
                .andExpect(status().is4xxClientError());

        // 4. Valid payment.authorized webhook updates Payment to AUTHORIZED while keeping order AWAITING_PAYMENT
        String authSig = RazorpaySignatureUtils.signPayload(authPayload, razorpayWebhookSecret);
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", authSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));

        assertThat(paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(checkoutSessionRepository.findByCheckoutReference(checkoutRef).orElseThrow().getStatus())
                .isEqualTo(CheckoutStatus.AWAITING_PAYMENT);

        // 5. Valid payment.captured webhook fulfills payment, deducts stock once, and issues invoice
        String capturePayload = """
                {"event":"payment.captured","id":"evt_cap_p6_001","payload":{"payment":{"entity":{"id":"pay_p6_live_001","order_id":"%s","amount":%d,"status":"captured"}}}}
                """.formatted(gatewayOrderId, amountPaise).trim();
        String captureSig = RazorpaySignatureUtils.signPayload(capturePayload, razorpayWebhookSecret);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", captureSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(capturePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));

        assertThat(paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        assertThat(checkoutSessionRepository.findByCheckoutReference(checkoutRef).orElseThrow().getStatus())
                .isEqualTo(CheckoutStatus.PAID);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);

        int stockAfterCapture = medicineBatchRepository.sumSellableQuantity(medId, LocalDate.now());
        assertThat(stockAfterCapture).isEqualTo(18);

        // 6. Duplicate webhook delivery ignored without re-processing or double stock deduction
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", captureSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(capturePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IGNORED_DUPLICATE"));
        assertThat(medicineBatchRepository.sumSellableQuantity(medId, LocalDate.now()))
                .isEqualTo(18);

        // 7. Out-of-order payment.failed webhook arriving AFTER capture is ignored safely
        String outOfOrderFailedPayload = """
                {"event":"payment.failed","id":"evt_fail_late_001","payload":{"payment":{"entity":{"id":"pay_p6_live_001","order_id":"%s","error_code":"GATEWAY_ERROR","error_description":"Delayed failure webhook"}}}}
                """.formatted(gatewayOrderId).trim();
        String failedSig = RazorpaySignatureUtils.signPayload(outOfOrderFailedPayload, razorpayWebhookSecret);
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", failedSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outOfOrderFailedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IGNORED_OUT_OF_ORDER"));
        assertThat(paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);

        // 8. Client confirmation retry after webhook capture stays idempotent
        String clientSig = RazorpaySignatureUtils.signPayload(gatewayOrderId + "|pay_p6_live_001", razorpayKeySecret);
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayOrderId": "%s",
                                  "razorpayPaymentId": "pay_p6_live_001",
                                  "razorpaySignature": "%s",
                                  "idempotencyKey": "retry-after-webhook"
                                }
                                """.formatted(gatewayOrderId, clientSig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        assertThat(medicineBatchRepository.sumSellableQuantity(medId, LocalDate.now()))
                .isEqualTo(18);

        // 9. Customer payment history & server verify endpoint
        String payRef = internalPayment.getPaymentReference();
        mockMvc.perform(get("/api/v1/customer/payments")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paymentReference").value(payRef))
                .andExpect(jsonPath("$.data[0].status").value("CAPTURED"));

        mockMvc.perform(post("/api/v1/customer/payments/" + payRef + "/verify")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CAPTURED"));

        // 10. Downloadable PDF Invoice + ownership protection + honest tax note
        mockMvc.perform(get("/api/v1/customer/orders/" + orderId + "/invoice")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.taxApplicable").value(true))
                .andExpect(jsonPath("$.data.taxNote").isNotEmpty());

        MvcResult pdfRes = mockMvc.perform(get("/api/v1/customer/orders/" + orderId + "/invoice/pdf")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] pdfBytes = pdfRes.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, Math.min(4, pdfBytes.length), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");

        mockMvc.perform(get("/api/v1/pharmacy/orders/" + orderId + "/invoice/pdf")
                        .header("Authorization", "Bearer " + pharmaToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customer/orders/" + orderId + "/invoice/pdf")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // 11. Cancel order -> idempotent refund request -> Admin Razorpay refund processing syncs states
        mockMvc.perform(post("/api/v1/customer/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ordered wrong strength\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundEligible").value(true));

        MvcResult ref1 = mockMvc.perform(post("/api/v1/customer/refunds/orders/" + orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need full refund for cancelled order\",\"idempotencyKey\":\"rfd-idem-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn();
        Long refundId = extractLong(ref1.getResponse().getContentAsString(), "\"id\":");

        MvcResult refReplay = mockMvc.perform(post("/api/v1/customer/refunds/orders/" + orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need full refund for cancelled order\",\"idempotencyKey\":\"rfd-idem-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(extractLong(refReplay.getResponse().getContentAsString(), "\"id\":")).isEqualTo(refundId);

        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/process")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Processed after cancellation verification\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));

        assertThat(refundRecordRepository.findById(refundId).orElseThrow().getStatus())
                .isEqualTo(RefundRecordStatus.PROCESSED);
        assertThat(paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        var updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getRefundStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);

        // Admin webhook audit log check
        mockMvc.perform(get("/api/v1/admin/payments/webhooks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // 12. Payment failure handling releases stock reservation cleanly
        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicineId\":" + medId + ",\"quantity\":3}"))
                .andExpect(status().isOk());
        MvcResult failedSessRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String failedCheckoutRef = extractString(failedSessRes.getResponse().getContentAsString(), "\"checkoutReference\":\"", "\"");
        int reservedAfterCreate = medicineBatchRepository.findAll().stream()
                .filter(b -> b.getMedicine().getId().equals(medId))
                .mapToInt(MedicineBatch::getReservedQuantity)
                .sum();
        assertThat(reservedAfterCreate).isEqualTo(3);

        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + failedCheckoutRef + "/payment-failed")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayPaymentId": "pay_fail_client_1",
                                  "errorCode": "BAD_REQUEST_ERROR",
                                  "errorDescription": "UPI collect declined by bank",
                                  "releaseStock": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        assertThat(checkoutSessionRepository.findByCheckoutReference(failedCheckoutRef).orElseThrow().getStatus())
                .isEqualTo(CheckoutStatus.FAILED);
        int reservedAfterFailure = medicineBatchRepository.findAll().stream()
                .filter(b -> b.getMedicine().getId().equals(medId))
                .mapToInt(MedicineBatch::getReservedQuantity)
                .sum();
        assertThat(reservedAfterFailure).isEqualTo(0);
    }

    private void addDeliveryZone(String pharmaToken, String name, String postal, String fee) throws Exception {
        mockMvc.perform(post("/api/v1/pharmacy/delivery-zones")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "zoneName": "%s",
                                  "postalCode": "%s",
                                  "city": "Bengaluru",
                                  "deliveryFee": %s,
                                  "minOrderAmount": 10.00,
                                  "estimatedDeliveryMinutes": 30,
                                  "active": true
                                }
                                """.formatted(name, postal, fee)))
                .andExpect(status().isCreated());
    }

    private Long createAndApproveMedicine(
            String adminToken,
            String pharmaToken,
            Long categoryId,
            String name,
            String classCode,
            boolean rxRequired,
            String price,
            String batchNum,
            int qty) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/pharmacy/medicines")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "%s",
                                  "brand": "MedBrand",
                                  "manufacturer": "MedMfg",
                                  "composition": "Paracetamol 650mg",
                                  "dosageForm": "Tablet",
                                  "packSize": "10 Tabs",
                                  "mrpPrice": %s,
                                  "sellingPrice": %s,
                                  "prescriptionRequired": %s,
                                  "classificationCode": "%s"
                                }
                                """.formatted(categoryId, name, price, price, rxRequired, classCode)))
                .andExpect(status().isCreated())
                .andReturn();
        Long medId = extractLong(res.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(post("/api/v1/admin/medicines/" + medId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pharmacy/inventory/batches")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "batchNumber": "%s",
                                  "manufacturingDate": "%s",
                                  "expiryDate": "%s",
                                  "quantity": %d,
                                  "purchasePrice": 15.00,
                                  "sellingPrice": %s
                                }
                                """.formatted(
                                medId,
                                batchNum,
                                LocalDate.now().minusMonths(1),
                                LocalDate.now().plusMonths(12),
                                qty,
                                price)))
                .andExpect(status().isCreated());
        return medId;
    }

    private Long addCustomerAddress(String token, String postal) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/customer/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Home",
                                  "recipientName": "Ananya Sharma",
                                  "recipientPhone": "+919876543210",
                                  "addressLine1": "80 Feet Road",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "%s"
                                }
                                """.formatted(postal)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLong(res.getResponse().getContentAsString(), "\"id\":");
    }

    private void registerAndActivateCustomer(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s",
                                  "phoneNumber": "+919900112233",
                                  "password": "StrongPass#123"
                                }
                                """.formatted(name, email)))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    private void registerAndApprovePharmacy(
            String adminToken,
            String email,
            String name,
            String license) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/pharmacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Owner %s",
                                  "email": "%s",
                                  "phoneNumber": "+919800112233",
                                  "password": "StrongPass#123",
                                  "pharmacyName": "%s",
                                  "addressLine": "20 Main Rd",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560034",
                                  "licenseNumber": "%s",
                                  "issuingAuthority": "State Drug Authority",
                                  "pharmacistName": "Dr. Pharmacist",
                                  "pharmacistRegistrationNumber": "REG-%s",
                                  "licenseExpiryDate": "%s"
                                }
                                """.formatted(name, email, name, license, license, LocalDate.now().plusYears(2))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        Long pharmacyId = pharmacyRepository.findByUserId(user.getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/pharmacies/" + pharmacyId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"Approved\"}"))
                .andExpect(status().isOk());
    }

    private String signInAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"omp27614@gmail.com\",\"password\":\"TestAdminPassword#2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractString(result.getResponse().getContentAsString(), "\"accessToken\":\"", "\"");
    }

    private String signInUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractString(result.getResponse().getContentAsString(), "\"accessToken\":\"", "\"");
    }

    private String extractString(String json, String prefix, String suffix) {
        int start = json.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += prefix.length();
        int end = json.indexOf(suffix, start);
        return json.substring(start, end);
    }

    private Long extractLong(String json, String prefix) {
        int start = json.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += prefix.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
