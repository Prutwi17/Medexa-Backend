package com.medexa.checkout;

import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItemRepository;
import com.medexa.checkout.service.CheckoutService;
import com.medexa.checkout.service.RazorpaySignatureUtils;
import com.medexa.customer.CustomerProfileRepository;
import com.medexa.customer.RecentlyViewedMedicineRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.prescription.PrescriptionOrderLinkRepository;
import com.medexa.prescription.PrescriptionRepository;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import com.medexa.wishlist.WishlistItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutAndOrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

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
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private CheckoutService checkoutService;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @BeforeEach
    @AfterEach
    void cleanup() {
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
        pharmacyRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void multiPharmacyCheckoutIdempotentPaymentStockReservationsAndOrderLifecycle() throws Exception {
        String adminToken = signInAdmin();

        MvcResult catRes = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Phase5 Cardiology",
                                  "description": "Cardiac and essential care",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = extractLong(catRes.getResponse().getContentAsString(), "\"id\":");

        Long pharmacy1Id = registerAndApprovePharmacy(adminToken, "p5med1@medexa-test.in", "MedOne Care", "LIC-P5-101");
        Long pharmacy2Id = registerAndApprovePharmacy(adminToken, "p5med2@medexa-test.in", "MedTwo Care", "LIC-P5-202");
        String pharma1Token = signInUser("p5med1@medexa-test.in", "StrongPass#123");
        String pharma2Token = signInUser("p5med2@medexa-test.in", "StrongPass#123");

        addDeliveryZone(pharma1Token, "Indiranagar Zone", "560038", "30.00");
        addDeliveryZone(pharma2Token, "Indiranagar Zone Two", "560038", "40.00");

        Long medRxId = createAndApproveMedicine(
                adminToken, pharma1Token, categoryId, "Atorva 20", "SCHEDULE_H", true, "200.00", "BATCH-P5-RX", 10
        );
        Long medOtcId = createAndApproveMedicine(
                adminToken, pharma2Token, categoryId, "Dolo 650 Strip", "OTC", false, "50.00", "BATCH-P5-OTC", 10
        );

        registerAndActivateCustomer("buyer@medexa-test.in", "Karthik Menon");
        String buyerToken = signInUser("buyer@medexa-test.in", "StrongPass#123");

        Long addressId = addCustomerAddress(buyerToken, "560038");

        // 1. Add OTC (4 * 50 = 200) and Rx (1 * 200 = 200) to cart; total subtotal = 400
        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 1
                                }
                                """.formatted(medRxId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 4
                                }
                                """.formatted(medOtcId)))
                .andExpect(status().isOk());

        // Checkout WITHOUT prescription on Rx item is blocked!
        mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d,
                                  "couponCode": "WELCOME10",
                                  "idempotencyKey": "idem-blocked-rx"
                                }
                                """.formatted(addressId)))
                .andExpect(status().isUnprocessableEntity());

        // Upload & approve prescription at Pharmacy 1 and attach to cart item
        Long rxId = uploadAndApproveRx(buyerToken, pharma1Token, pharmacy1Id);
        MvcResult cartRes = mockMvc.perform(get("/api/v1/customer/cart")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long rxCartItemId = extractCartItemIdForMedicine(cartRes.getResponse().getContentAsString(), medRxId);

        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 1,
                                  "prescriptionId": %d
                                }
                                """.formatted(medRxId, rxId)))
                .andExpect(status().isOk());
        assertThat(rxCartItemId).isNotNull();

        // Reset qty of medRx back to 1 cleanly (above add incremented to 2 -> adjust to 1 via PUT)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customer/cart/items/" + rxCartItemId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 1,
                                  "prescriptionId": %d
                                }
                                """.formatted(rxId)))
                .andExpect(status().isOk());

        // 2. Preview checkout: 400 subtotal, WELCOME10 (10%) = 40 discount, delivery = 30+40 = 70, tax 5% on 360 = 18, grand = 448
        mockMvc.perform(post("/api/v1/customer/checkout/preview")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d,
                                  "couponCode": "WELCOME10"
                                }
                                """.formatted(addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutAllowed").value(true))
                .andExpect(jsonPath("$.data.couponApplied").value(true))
                .andExpect(jsonPath("$.data.itemsSubtotal").value(400.00))
                .andExpect(jsonPath("$.data.discountAmount").value(40.00))
                .andExpect(jsonPath("$.data.deliveryFeeTotal").value(70.00))
                .andExpect(jsonPath("$.data.taxAmount").value(18.00))
                .andExpect(jsonPath("$.data.grandTotal").value(448.00));

        // 3. Create checkout session with idempotency key -> calling twice does not double-reserve stock
        MvcResult sessionRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d,
                                  "couponCode": "WELCOME10",
                                  "idempotencyKey": "idem-phase5-001"
                                }
                                """.formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andReturn();
        String sessionJson = sessionRes.getResponse().getContentAsString();
        String checkoutRef = extractString(sessionJson, "\"checkoutReference\":\"", "\"");
        String razorpayOrderId = extractString(sessionJson, "\"razorpayOrderId\":\"", "\"");

        mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d,
                                  "couponCode": "WELCOME10",
                                  "idempotencyKey": "idem-phase5-001"
                                }
                                """.formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.checkoutReference").value(checkoutRef));

        // Verify FEFO reservation decreased available quantity: 10 - 1 = 9, 10 - 4 = 6
        assertThat(medicineRepository.findById(medRxId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(9);
        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(6);

        // 4. Confirm payment: invalid signature rejected; valid HMAC confirms & commits stock; retry is idempotent
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayPaymentId": "pay_test_12345",
                                  "razorpayOrderId": "%s",
                                  "razorpaySignature": "tampered_invalid_signature"
                                }
                                """.formatted(razorpayOrderId)))
                .andExpect(status().isBadRequest());

        String validSignature = RazorpaySignatureUtils.signPayload(
                razorpayOrderId + "|pay_test_12345",
                razorpayKeySecret
        );

        MvcResult paidRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayPaymentId": "pay_test_12345",
                                  "razorpayOrderId": "%s",
                                  "razorpaySignature": "%s"
                                }
                                """.formatted(razorpayOrderId, validSignature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.orders[0].status").value("PLACED"))
                .andExpect(jsonPath("$.data.orders[1].status").value("PLACED"))
                .andReturn();

        // Retry confirm-payment -> must return PAID cleanly with identical stock (9 and 6)
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "razorpayPaymentId": "pay_test_12345",
                                  "razorpayOrderId": "%s",
                                  "razorpaySignature": "%s"
                                }
                                """.formatted(razorpayOrderId, validSignature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        assertThat(medicineRepository.findById(medRxId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(9);
        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(6);

        // Cart cleared after verified checkout
        mockMvc.perform(get("/api/v1/customer/cart")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItemCount").value(0));

        // Locate child order IDs for pharmacy 1 and pharmacy 2
        var sessionEntity = checkoutSessionRepository.findByCheckoutReference(checkoutRef).orElseThrow();
        var orders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(sessionEntity.getId());
        Long orderP1Id = orders.stream().filter(o -> o.getPharmacy().getId().equals(pharmacy1Id)).findFirst().orElseThrow().getId();
        Long orderP2Id = orders.stream().filter(o -> o.getPharmacy().getId().equals(pharmacy2Id)).findFirst().orElseThrow().getId();
        assertThat(paidRes).isNotNull();

        // 5. Pharmacy 1 fulfillment: PLACED -> PHARMACY_ACCEPTED -> PREPARING -> READY_FOR_PICKUP
        mockMvc.perform(post("/api/v1/pharmacy/orders/" + orderP1Id + "/decision")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accepted": true,
                                  "reason": "Rx verified and batches ready"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PHARMACY_ACCEPTED"));

        mockMvc.perform(post("/api/v1/pharmacy/orders/" + orderP1Id + "/status")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PREPARING",
                                  "notes": "Packing batch items"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"));

        mockMvc.perform(post("/api/v1/pharmacy/orders/" + orderP1Id + "/status")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "READY_FOR_PICKUP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.data.deliveryAssignmentStatus").value("AWAITING_PICKUP"));

        // Customer cannot cancel READY_FOR_PICKUP order
        mockMvc.perform(post("/api/v1/customer/orders/" + orderP1Id + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Changed mind"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // 6. Customer cancels Pharmacy 2 PLACED order -> stock restored from 6 back to 10 & refund becomes ELIGIBLE -> REQUESTED
        mockMvc.perform(post("/api/v1/customer/orders/" + orderP2Id + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Ordered duplicate strip"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundEligible").value(true))
                .andExpect(jsonPath("$.data.refundStatus").value("ELIGIBLE"));

        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(10);

        mockMvc.perform(post("/api/v1/customer/orders/" + orderP2Id + "/refund-request")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Refund back to UPI source"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"));

        // 7. Test reservation expiry releases stock automatically
        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 3
                                }
                                """.formatted(medOtcId)))
                .andExpect(status().isOk());

        MvcResult expSessionRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d
                                }
                                """.formatted(addressId)))
                .andExpect(status().isCreated())
                .andReturn();
        String expRef = extractString(expSessionRes.getResponse().getContentAsString(), "\"checkoutReference\":\"", "\"");
        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(7);

        // Cancel & verify release restores back to 10; plus direct expiry test
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + expRef + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Testing release"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(10);

        // Expiry scheduler check
        MvcResult timedRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d
                                }
                                """.formatted(addressId)))
                .andExpect(status().isCreated())
                .andReturn();
        String timedRef = extractString(timedRes.getResponse().getContentAsString(), "\"checkoutReference\":\"", "\"");
        var timedEntity = checkoutSessionRepository.findByCheckoutReference(timedRef).orElseThrow();
        timedEntity.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        checkoutSessionRepository.save(timedEntity);
        assertThat(checkoutService.expireTimedOutCheckouts()).isEqualTo(1);
        assertThat(checkoutSessionRepository.findByCheckoutReference(timedRef).orElseThrow().getStatus())
                .isEqualTo(CheckoutStatus.EXPIRED);
        assertThat(medicineRepository.findById(medOtcId).orElseThrow().getTotalAvailableQuantity()).isEqualTo(10);

        // 8. Admin order monitoring
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/v1/admin/orders/checkouts/" + checkoutRef)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(2));
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
                                  "composition": "Active Compound 20mg",
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
                                  "purchasePrice": 10.00,
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

    private Long uploadAndApproveRx(String customerToken, String pharmaToken, Long pharmacyId) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "rx-p5.pdf", "application/pdf", "%PDF-1.4 Test".getBytes()
        );
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/customer/prescriptions")
                        .file(pdf)
                        .header("Authorization", "Bearer " + customerToken)
                        .param("pharmacyId", String.valueOf(pharmacyId))
                        .param("patientName", "Karthik Menon")
                        .param("doctorName", "Dr. Rao"))
                .andExpect(status().isCreated())
                .andReturn();
        Long rxId = extractLong(uploadRes.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(post("/api/v1/pharmacy/prescriptions/" + rxId + "/review")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());
        return rxId;
    }

    private Long addCustomerAddress(String token, String postal) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/customer/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Home",
                                  "recipientName": "Karthik Menon",
                                  "recipientPhone": "+919876543210",
                                  "addressLine1": "100 Feet Road",
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

    private Long registerAndApprovePharmacy(
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
                                  "postalCode": "560038",
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
        return pharmacyId;
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

    private Long extractCartItemIdForMedicine(String json, Long medId) {
        String marker = "\"medicineId\":" + medId;
        int medPos = json.indexOf(marker);
        assertThat(medPos).isGreaterThan(0);
        String prefix = "\"cartItemId\":";
        int start = json.lastIndexOf(prefix, medPos);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += prefix.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
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
