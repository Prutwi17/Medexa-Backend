package com.medexa.delivery;

import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItemRepository;
import com.medexa.category.CategoryRepository;
import com.medexa.checkout.CheckoutSessionRepository;
import com.medexa.checkout.service.RazorpaySignatureUtils;
import com.medexa.customer.CustomerProfileRepository;
import com.medexa.customer.RecentlyViewedMedicineRepository;
import com.medexa.invoice.InvoiceRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.payment.PaymentEventRepository;
import com.medexa.payment.PaymentRepository;
import com.medexa.payment.RazorpayWebhookEventRepository;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.prescription.PrescriptionOrderLinkRepository;
import com.medexa.prescription.PrescriptionRepository;
import com.medexa.refund.RefundRecordRepository;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderDeliveryZoneRepository;
import com.medexa.rider.RiderEarningRepository;
import com.medexa.rider.RiderRepository;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import com.medexa.wishlist.WishlistItemRepository;
import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiderAndDeliveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RiderDeliveryZoneRepository riderZoneRepository;

    @Autowired
    private RiderEarningRepository riderEarningRepository;

    @Autowired
    private DeliveryEventRepository deliveryEventRepository;

    @Autowired
    private DeliveryAssignmentAttemptRepository attemptRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

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

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private RecentlyViewedMedicineRepository recentlyViewedRepository;

    @Autowired
    private PrescriptionOrderLinkRepository prescriptionOrderLinkRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @BeforeEach
    @AfterEach
    void cleanup() {
        riderEarningRepository.deleteAll();
        deliveryEventRepository.deleteAll();
        attemptRepository.deleteAll();
        deliveryRepository.deleteAll();
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
        riderZoneRepository.deleteAll();
        riderRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void fullRiderOnboardingRankingTimeoutReassignmentOtpProofAndEarnings() throws Exception {
        String adminToken = signInAdmin();

        // 1. Create verified category, pharmacy (at Bengaluru central coordinates), medicine & buyer
        MvcResult catRes = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phase7 Antibiotics\",\"description\":\"Essential care\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = extractLong(catRes.getResponse().getContentAsString(), "\"id\":");

        Long pharmacyId = registerAndApprovePharmacy(adminToken, "p7pharma@medexa-test.in", "Apex Care Pharmacy", "LIC-P7-101");
        Pharmacy pharma = pharmacyRepository.findById(pharmacyId).orElseThrow();
        pharma.setLatitude(new BigDecimal("12.971600"));
        pharma.setLongitude(new BigDecimal("77.594600"));
        pharmacyRepository.save(pharma);

        String pharmaToken = signInUser("p7pharma@medexa-test.in", "StrongPass#123");
        addDeliveryZone(pharmaToken, "MG Road Zone", "560001", "30.00");

        Long medId = createAndApproveMedicine(
                adminToken, pharmaToken, categoryId, "Azithro 500", "OTC", false, "120.00", "BATCH-P7-1", 15
        );

        registerAndActivateCustomer("p7buyer@medexa-test.in", "Rohan Kulkarni");
        String buyerToken = signInUser("p7buyer@medexa-test.in", "StrongPass#123");
        Long addressId = addCustomerAddress(buyerToken, "560001");

        // 2. Register Rider A (close: ~0.6 km) & Rider B (farther: ~5 km), verify KYC/zones, bring ONLINE
        Long riderAId = registerAndApproveRider(
                adminToken, "rider.a@medexa-test.in", "Arjun Near", "KA01AA1001", "DL-P7-001",
                "12.976000", "77.599000", "560001"
        );
        Long riderBId = registerAndApproveRider(
                adminToken, "rider.b@medexa-test.in", "Vikram Far", "KA01BB2002", "DL-P7-002",
                "12.935200", "77.624500", "560001"
        );
        String riderAToken = signInUser("rider.a@medexa-test.in", "StrongPass#123");
        String riderBToken = signInUser("rider.b@medexa-test.in", "StrongPass#123");

        // Verify online toggle works cleanly
        mockMvc.perform(patch("/api/v1/rider/availability")
                        .header("Authorization", "Bearer " + riderAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityStatus\":\"ONLINE\",\"currentLatitude\":12.976000,\"currentLongitude\":77.599000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availabilityStatus").value("ONLINE"));

        mockMvc.perform(patch("/api/v1/rider/availability")
                        .header("Authorization", "Bearer " + riderBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityStatus\":\"ONLINE\",\"currentLatitude\":12.935200,\"currentLongitude\":77.624500}"))
                .andExpect(status().isOk());

        // 3. Place paid order -> Pharmacy accepts -> marks READY_FOR_PICKUP
        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicineId\":" + medId + ",\"quantity\":1}"))
                .andExpect(status().isOk());

        MvcResult sessRes = mockMvc.perform(post("/api/v1/customer/checkout/sessions")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessJson = sessRes.getResponse().getContentAsString();
        String checkoutRef = extractString(sessJson, "\"checkoutReference\":\"", "\"");
        String rzpOrderId = extractString(sessJson, "\"razorpayOrderId\":\"", "\"");
        Long orderId = extractLong(sessJson.substring(sessJson.indexOf("\"orders\":")), "\"id\":");

        String sig = RazorpaySignatureUtils.signPayload(rzpOrderId + "|pay_p7_001", razorpayKeySecret);
        mockMvc.perform(post("/api/v1/customer/checkout/sessions/" + checkoutRef + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpayOrderId":"%s","razorpayPaymentId":"pay_p7_001","razorpaySignature":"%s"}
                                """.formatted(rzpOrderId, sig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/v1/pharmacy/orders/" + orderId + "/decision")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk());

        // Marking READY_FOR_PICKUP auto-ranks Rider A (closer distance) and moves order -> RIDER_ASSIGNED
        mockMvc.perform(post("/api/v1/pharmacy/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + pharmaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_FOR_PICKUP\",\"notes\":\"Packed for rider\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RIDER_ASSIGNED"));

        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(delivery.getAssignedRider().getId()).isEqualTo(riderAId);

        // Privacy check: Rider A sees masked address while ASSIGNED (not accepted yet); Rider B is denied access
        mockMvc.perform(get("/api/v1/rider/deliveries/" + delivery.getId())
                        .header("Authorization", "Bearer " + riderAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullCustomerAddressRevealed").value(false));

        mockMvc.perform(get("/api/v1/rider/deliveries/" + delivery.getId())
                        .header("Authorization", "Bearer " + riderBToken))
                .andExpect(status().isNotFound());

        // 4. Rider A rejects -> Automatic reassignment picks Rider B!
        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/decision")
                        .header("Authorization", "Bearer " + riderAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false,\"reason\":\"Flat tyre near junction\"}"))
                .andExpect(status().isOk());

        Delivery afterReject = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterReject.getAssignedRider().getId()).isEqualTo(riderBId);
        assertThat(afterReject.getAssignmentAttempts()).isEqualTo(2);

        // 5. Simulate acceptance timeout on Rider B -> Timeout sweep marks TIMED_OUT -> REASSIGNMENT_REQUIRED
        afterReject.setAssignmentExpiresAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        deliveryRepository.save(afterReject);

        mockMvc.perform(post("/api/v1/admin/deliveries/sweep-timeouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reassignedCount").value(1));

        Delivery afterTimeout = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterTimeout.getStatus()).isEqualTo(DeliveryStatus.REASSIGNMENT_REQUIRED);
        assertThat(afterTimeout.getAssignedRider()).isNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);

        // 6. Onboard Rider C -> Retry auto-assignment assigns Rider C
        Long riderCId = registerAndApproveRider(
                adminToken, "rider.c@medexa-test.in", "Deepak Express", "KA01CC3003", "DL-P7-003",
                "12.971800", "77.595000", "560001"
        );
        String riderCToken = signInUser("rider.c@medexa-test.in", "StrongPass#123");
        mockMvc.perform(patch("/api/v1/rider/availability")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilityStatus\":\"ONLINE\",\"currentLatitude\":12.971800,\"currentLongitude\":77.595000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/deliveries/" + delivery.getId() + "/retry-auto")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedRiderId").value(riderCId));

        // 7. Rider C accepts -> full address revealed -> pickup confirmed -> out for delivery + OTP issued
        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/decision")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.fullCustomerAddressRevealed").value(true))
                .andExpect(jsonPath("$.data.customerAddress").value("42 Church Street"));

        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/pickup")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Collected sealed pharmacy bag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"));

        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/out-for-delivery")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":12.973000,\"longitude\":77.598000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.otpPendingVerification").value(true));

        // 8. Customer views tracking & active 6-digit OTP; wrong OTP increments retry counter; proof + valid OTP completes!
        MvcResult trackRes = mockMvc.perform(get("/api/v1/customer/orders/" + orderId + "/tracking")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryOtp").isNotEmpty())
                .andReturn();
        String customerOtp = extractString(trackRes.getResponse().getContentAsString(), "\"deliveryOtp\":\"", "\"");

        // Wrong OTP fails cleanly and decrements remaining retries
        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/complete")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(deliveryRepository.findById(delivery.getId()).orElseThrow().getOtpAttempts()).isEqualTo(1);

        // Attach handover proof image
        MockMultipartFile proofImg = new MockMultipartFile(
                "file", "handover.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
        mockMvc.perform(multipart("/api/v1/rider/deliveries/" + delivery.getId() + "/proof")
                        .file(proofImg)
                        .header("Authorization", "Bearer " + riderCToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDeliveryProof").value(true));

        // Valid OTP completes delivery, clears plaintext OTP, marks order DELIVERED, accrues rider earning!
        mockMvc.perform(post("/api/v1/rider/deliveries/" + delivery.getId() + "/complete")
                        .header("Authorization", "Bearer " + riderCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"otp":"%s","recipientName":"Rohan Kulkarni","notes":"Handed at main gate"}
                                """.formatted(customerOtp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.proofType").value("PHOTO_AND_OTP"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(deliveryRepository.findById(delivery.getId()).orElseThrow().getCustomerOtpCode()).isNull();
        assertThat(riderRepository.findById(riderCId).orElseThrow().getAvailabilityStatus())
                .isEqualTo(RiderAvailabilityStatus.ONLINE);

        // Earnings endpoint verifies daily/monthly accrual
        mockMvc.perform(get("/api/v1/rider/earnings")
                        .header("Authorization", "Bearer " + riderCToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDeliveries").value(1))
                .andExpect(jsonPath("$.data.todayDeliveries").value(1));

        // Customer & Admin proof endpoints work; unrelated rider blocked
        mockMvc.perform(get("/api/v1/customer/orders/" + orderId + "/tracking/proof")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rider/deliveries/" + delivery.getId() + "/proof")
                        .header("Authorization", "Bearer " + riderBToken))
                .andExpect(status().isNotFound());
    }

    private Long registerAndApproveRider(
            String adminToken,
            String email,
            String name,
            String vehicleReg,
            String dlNo,
            String lat,
            String lng,
            String postalCode) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/rider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s",
                                  "phoneNumber": "+919811223344",
                                  "password": "StrongPass#123",
                                  "vehicleType": "MOTORCYCLE",
                                  "vehicleRegistrationNumber": "%s",
                                  "drivingLicenseNumber": "%s",
                                  "serviceCity": "Bengaluru"
                                }
                                """.formatted(name, email, vehicleReg, dlNo)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        Long riderId = riderRepository.findByUserId(user.getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/riders/" + riderId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"KYC & DL verified\",\"baseFeePerDelivery\":45.00,\"maxConcurrentDeliveries\":2}"))
                .andExpect(status().isOk());

        String riderToken = signInUser(email, "StrongPass#123");

        // Submit full KYC fields, document, coordinates, and delivery zone
        mockMvc.perform(put("/api/v1/rider/profile")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "+919811223344",
                                  "vehicleType": "MOTORCYCLE",
                                  "vehicleRegistrationNumber": "%s",
                                  "vehicleModel": "Honda Activa 6G",
                                  "serviceCity": "Bengaluru",
                                  "identityDocumentType": "AADHAAR",
                                  "identityDocumentNumber": "XXXX-XXXX-9911",
                                  "drivingLicenseNumber": "%s",
                                  "drivingLicenseExpiry": "%s",
                                  "currentLatitude": %s,
                                  "currentLongitude": %s
                                }
                                """.formatted(vehicleReg, dlNo, LocalDate.now().plusYears(3), lat, lng)))
                .andExpect(status().isOk());

        MockMultipartFile dlDoc = new MockMultipartFile(
                "file", "dl.pdf", "application/pdf", "%PDF-1.4 DL".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/rider/profile/documents")
                        .file(dlDoc)
                        .param("docType", "DRIVING_LICENSE")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDrivingLicenseDocument").value(true));

        mockMvc.perform(post("/api/v1/rider/delivery-zones")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "zoneName": "Central Hub",
                                  "city": "Bengaluru",
                                  "postalCode": "%s",
                                  "priorityOrder": 1,
                                  "active": true
                                }
                                """.formatted(postalCode)))
                .andExpect(status().isCreated());

        return riderId;
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
                                  "composition": "Active Compound 500mg",
                                  "dosageForm": "Tablet",
                                  "packSize": "5 Tabs",
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
                                  "purchasePrice": 20.00,
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
                                  "recipientName": "Rohan Kulkarni",
                                  "recipientPhone": "+919876543210",
                                  "addressLine1": "42 Church Street",
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
                                  "addressLine": "15 MG Road",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560001",
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
