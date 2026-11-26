package com.medexa.prescription;

import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItemRepository;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.customer.CustomerProfileRepository;
import com.medexa.customer.RecentlyViewedMedicineRepository;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import com.medexa.wishlist.WishlistItemRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrescriptionAndCustomerStorefrontIntegrationTest {

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
    private PrescriptionService prescriptionService;

    @BeforeEach
    void cleanup() {
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
    void fullPrescriptionComplianceAndCustomerStorefrontWorkflow() throws Exception {
        String adminToken = signInAdmin();

        // 1. Admin creates category and verifies classification rules block auto-substitution on Schedule H
        MvcResult catRes = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Phase4 Antibiotics",
                                  "description": "Scheduled antibacterial care",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = extractLong(catRes.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(get("/api/v1/public/medicines/classifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.classificationCode=='SCHEDULE_H')].autoSubstitutionAllowed").value(false));

        mockMvc.perform(put("/api/v1/admin/medicines/classifications/SCHEDULE_H")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Schedule H Prescription Medicine",
                                  "prescriptionRequired": true,
                                  "defaultRxValidityDays": 180,
                                  "maxUnitsPerOrder": 30,
                                  "autoSubstitutionAllowed": true,
                                  "requiresPharmacistLicenseCheck": true,
                                  "warningText": "Cannot auto substitute"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // 2. Setup two verified pharmacies (Pharmacy 1 & Pharmacy 2) with delivery zones and medicines
        Long pharmacy1Id = registerAndApprovePharmacy(adminToken, "pharma1@medexa-test.in", "MedCare One", "LIC-MCO-101");
        Long pharmacy2Id = registerAndApprovePharmacy(adminToken, "pharma2@medexa-test.in", "MedCare Two", "LIC-MCT-202");

        String pharma1Token = signInUser("pharma1@medexa-test.in", "StrongPass#123");
        String pharma2Token = signInUser("pharma2@medexa-test.in", "StrongPass#123");

        mockMvc.perform(post("/api/v1/pharmacy/delivery-zones")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "zoneName": "Indiranagar Central",
                                  "postalCode": "560038",
                                  "city": "Bengaluru",
                                  "deliveryFee": 35.00,
                                  "minOrderAmount": 100.00,
                                  "estimatedDeliveryMinutes": 35,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated());

        // Pharmacy 1 lists a Schedule H medicine even if prescriptionRequired=false is attempted -> classification forces Rx=true
        MvcResult rxMedRes = mockMvc.perform(post("/api/v1/pharmacy/medicines")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "Augmentin 625 Duo",
                                  "brand": "GSK",
                                  "manufacturer": "GlaxoSmithKline",
                                  "composition": "Amoxicillin 500mg + Clavulanic Acid 125mg",
                                  "dosageForm": "Tablet",
                                  "packSize": "10 Tablets",
                                  "description": "Broad-spectrum antibiotic",
                                  "mrpPrice": 220.00,
                                  "sellingPrice": 195.00,
                                  "prescriptionRequired": false,
                                  "classificationCode": "SCHEDULE_H"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prescriptionRequired").value(true))
                .andExpect(jsonPath("$.data.classificationCode").value("SCHEDULE_H"))
                .andReturn();
        Long rxMedId = extractLong(rxMedRes.getResponse().getContentAsString(), "\"id\":");
        String rxMedSlug = extractString(rxMedRes.getResponse().getContentAsString(), "\"slug\":\"", "\"");

        // Pharmacy 2 lists an OTC medicine
        MvcResult otcMedRes = mockMvc.perform(post("/api/v1/pharmacy/medicines")
                        .header("Authorization", "Bearer " + pharma2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "Electral ORS Sachet",
                                  "brand": "FDC",
                                  "manufacturer": "FDC Ltd",
                                  "composition": "WHO ORS Electrolyte Formula",
                                  "dosageForm": "Powder",
                                  "packSize": "21.8 g",
                                  "description": "Oral rehydration salts",
                                  "mrpPrice": 24.00,
                                  "sellingPrice": 22.00,
                                  "prescriptionRequired": false,
                                  "classificationCode": "OTC"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long otcMedId = extractLong(otcMedRes.getResponse().getContentAsString(), "\"id\":");

        approveMedicineAndAddStock(adminToken, pharma1Token, rxMedId, "BATCH-RX-1", 25);
        approveMedicineAndAddStock(adminToken, pharma2Token, otcMedId, "BATCH-OTC-1", 40);

        // 3. Register two customers (Customer 1 owns Rx, Customer 2 tests ownership boundary)
        Long customer1Id = registerAndSignInCustomerId("cust1@medexa-test.in", "Aarav Rao");
        String cust1Token = signInUser("cust1@medexa-test.in", "StrongPass#123");
        registerAndSignInCustomerId("cust2@medexa-test.in", "Sneha Nair");
        String cust2Token = signInUser("cust2@medexa-test.in", "StrongPass#123");

        // 4. Storefront home + detail view + public delivery zone check
        mockMvc.perform(get("/api/v1/public/storefront/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[?(@.name=='Phase4 Antibiotics')]").exists())
                .andExpect(jsonPath("$.data.popularMedicines").isNotEmpty());

        mockMvc.perform(get("/api/v1/public/medicines/" + rxMedId + "/images"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/delivery-zones/check").param("postalCode", "560038"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceable").value(true))
                .andExpect(jsonPath("$.data.pharmacyCount").value(1));

        // 5. Customer 1 records recently viewed + updates profile + saves multiple addresses
        mockMvc.perform(get("/api/v1/public/medicines/" + rxMedSlug)
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoSubstitutionAllowed").value(false));

        mockMvc.perform(get("/api/v1/customer/recently-viewed")
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(rxMedId));

        mockMvc.perform(put("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Aarav K Rao",
                                  "phoneNumber": "+919876543210",
                                  "bloodGroup": "O+",
                                  "allergies": "Sulfa drugs",
                                  "preferredLanguage": "English"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodGroup").value("O+"));

        MvcResult addr1Res = mockMvc.perform(post("/api/v1/customer/addresses")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Home",
                                  "recipientName": "Aarav K Rao",
                                  "recipientPhone": "+919876543210",
                                  "addressLine1": "12th Main HAL 2nd Stage",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560038"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.defaultAddress").value(true))
                .andExpect(jsonPath("$.data.serviceable").value(true))
                .andReturn();
        Long addr1Id = extractLong(addr1Res.getResponse().getContentAsString(), "\"id\":");

        MvcResult addr2Res = mockMvc.perform(post("/api/v1/customer/addresses")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Office",
                                  "recipientName": "Aarav K Rao",
                                  "recipientPhone": "+919876543210",
                                  "addressLine1": "Outer Ring Road",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560103",
                                  "defaultAddress": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceable").value(false))
                .andReturn();
        Long addr2Id = extractLong(addr2Res.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(patch("/api/v1/customer/addresses/" + addr2Id + "/default")
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
        mockMvc.perform(patch("/api/v1/customer/addresses/" + addr1Id + "/default")
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk());

        // 6. Prescription upload (PDF), ownership check, pharmacy personnel review & dispatch block
        MockMultipartFile rxPdf = new MockMultipartFile(
                "file",
                "dr-sharma-rx.pdf",
                "application/pdf",
                "%PDF-1.4\nSample Clinical Prescription Content".getBytes()
        );

        MvcResult rxUploadRes = mockMvc.perform(multipart("/api/v1/customer/prescriptions")
                        .file(rxPdf)
                        .header("Authorization", "Bearer " + cust1Token)
                        .param("pharmacyId", String.valueOf(pharmacy1Id))
                        .param("patientName", "Aarav K Rao")
                        .param("patientAge", "32")
                        .param("doctorName", "Dr. Meera Sharma")
                        .param("doctorRegistrationNumber", "KMC-45891")
                        .param("issuedDate", LocalDate.now().toString())
                        .param("validUntil", LocalDate.now().plusDays(30).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.auditHistory[0].action").value("UPLOADED"))
                .andReturn();
        Long rxId = extractLong(rxUploadRes.getResponse().getContentAsString(), "\"id\":");
        String storedFileUrl = prescriptionRepository.findById(rxId).orElseThrow().getFileUrl();

        // Direct generic /api/v1/files/prescriptions/* must be denied!
        mockMvc.perform(get(storedFileUrl)
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isForbidden());

        // Customer 2 cannot read or download Customer 1's prescription
        mockMvc.perform(get("/api/v1/customer/prescriptions/" + rxId)
                        .header("Authorization", "Bearer " + cust2Token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customer/prescriptions/" + rxId + "/document")
                        .header("Authorization", "Bearer " + cust2Token))
                .andExpect(status().isForbidden());

        // Customer 1 can download own PDF document
        mockMvc.perform(get("/api/v1/customer/prescriptions/" + rxId + "/document")
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Pharmacy 2 is NOT the assigned pharmacy -> forbidden
        mockMvc.perform(post("/api/v1/pharmacy/prescriptions/" + rxId + "/review")
                        .header("Authorization", "Bearer " + pharma2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
                .andExpect(status().isForbidden());

        // Dispatch before approval MUST be blocked!
        mockMvc.perform(post("/api/v1/customer/prescriptions/validate-dispatch")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "prescriptionId": %d
                                }
                                """.formatted(rxMedId, rxId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(false));

        assertThatThrownBy(() -> prescriptionService.linkPrescriptionToOrder(
                customer1Id, rxMedId, rxId, 9001L, "ORD-9001"
        )).isInstanceOf(BusinessRuleException.class);

        // Pharmacy 1 rejects without reason -> 400; approves with authorized pharmacist license -> APPROVED
        mockMvc.perform(post("/api/v1/pharmacy/prescriptions/" + rxId + "/review")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/pharmacy/prescriptions/" + rxId + "/review")
                        .header("Authorization", "Bearer " + pharma1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "reviewNotes": "Verified dosage and doctor registration"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.pharmacistRegistrationNumber").value("REG-LIC-MCO-101"));

        // Now dispatch eligibility succeeds and links prescription to order
        mockMvc.perform(post("/api/v1/customer/prescriptions/validate-dispatch")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "prescriptionId": %d
                                }
                                """.formatted(rxMedId, rxId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(true));

        prescriptionService.linkPrescriptionToOrder(customer1Id, rxMedId, rxId, 9001L, "ORD-9001");
        assertThat(prescriptionOrderLinkRepository.findByOrderId(9001L)).hasSize(1);

        // 7. Wishlist & Multi-Pharmacy Cart
        mockMvc.perform(post("/api/v1/customer/wishlist/" + rxMedId)
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.medicine.id").value(rxMedId));

        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 2,
                                  "prescriptionId": %d
                                }
                                """.formatted(rxMedId, rxId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pharmacyGroups[0].items[0].dispatchEligible").value(true));

        mockMvc.perform(post("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "quantity": 3
                                }
                                """.formatted(otcMedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pharmacyCount").value(2))
                .andExpect(jsonPath("$.data.totalItemCount").value(5));

        mockMvc.perform(delete("/api/v1/customer/wishlist/" + rxMedId)
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk());
    }

    private Long registerAndSignInCustomerId(String email, String name) throws Exception {
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
        return user.getId();
    }

    private Long registerAndApprovePharmacy(
            String adminToken,
            String email,
            String pharmacyName,
            String licenseNumber) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/pharmacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Owner %s",
                                  "email": "%s",
                                  "phoneNumber": "+919800112233",
                                  "password": "StrongPass#123",
                                  "pharmacyName": "%s",
                                  "addressLine": "100 Main Street",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560038",
                                  "licenseNumber": "%s",
                                  "issuingAuthority": "State Drug Authority",
                                  "pharmacistName": "Dr. Registered Pharmacist",
                                  "pharmacistRegistrationNumber": "REG-%s",
                                  "licenseExpiryDate": "%s"
                                }
                                """.formatted(pharmacyName, email, pharmacyName, licenseNumber, licenseNumber,
                                LocalDate.now().plusYears(2))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        Long pharmacyId = pharmacyRepository.findByUserId(user.getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/pharmacies/" + pharmacyId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "notes": "Verified pharmacy license"
                                }
                                """))
                .andExpect(status().isOk());
        return pharmacyId;
    }

    private void approveMedicineAndAddStock(
            String adminToken,
            String pharmaToken,
            Long medicineId,
            String batchNumber,
            int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/admin/medicines/" + medicineId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
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
                                  "sellingPrice": 20.00
                                }
                                """.formatted(
                                medicineId,
                                batchNumber,
                                LocalDate.now().minusMonths(1),
                                LocalDate.now().plusMonths(12),
                                quantity)))
                .andExpect(status().isCreated());
    }

    private String signInAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "omp27614@gmail.com",
                                  "password": "TestAdminPassword#2026"
                                }
                                """))
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
