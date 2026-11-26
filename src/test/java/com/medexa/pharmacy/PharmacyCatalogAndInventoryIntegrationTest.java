package com.medexa.pharmacy;

import com.medexa.inventory.dto.InventoryDtos.BatchAllocation;
import com.medexa.inventory.service.InventoryService;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineRepository;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class PharmacyCatalogAndInventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void cleanup() {
        medicineRepository.deleteAll();
        pharmacyRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void fullPharmacyApprovalCategoryMedicineCatalogAndFefoInventoryFlow() throws Exception {
        String adminToken = signInAdmin();

        // 1. Admin creates category and tests unique name enforcement
        MvcResult catResult = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Antibiotics",
                                  "description": "Prescription antibacterial medicines",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("antibiotics"))
                .andReturn();
        Long categoryId = extractLong(catResult.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Antibiotics"
                                }
                                """))
                .andExpect(status().isConflict());

        // 2. Register Pharmacy A and Pharmacy B
        Long pharmacyAId = registerPharmacy("healthplus@medexa-test.in", "HealthPlus Pharmacy", "LIC-HP-001");
        Long pharmacyBId = registerPharmacy("wellness@medexa-test.in", "Wellness Pharmacy", "LIC-WL-002");

        // Verify both emails so login depends purely on admin approval
        activateEmail("healthplus@medexa-test.in");
        activateEmail("wellness@medexa-test.in");

        // Admin approves both pharmacies
        approvePharmacy(adminToken, pharmacyAId);
        approvePharmacy(adminToken, pharmacyBId);

        String pharmacyAToken = signInUser("healthplus@medexa-test.in", "StrongPass#123");
        String pharmacyBToken = signInUser("wellness@medexa-test.in", "StrongPass#123");

        // 3. Pharmacy A creates a delivery zone
        mockMvc.perform(post("/api/v1/pharmacy/delivery-zones")
                        .header("Authorization", "Bearer " + pharmacyAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "zoneName": "Indiranagar Central",
                                  "postalCode": "560038",
                                  "city": "Bengaluru",
                                  "deliveryFee": 25.00,
                                  "minOrderAmount": 150.00,
                                  "estimatedDeliveryMinutes": 35
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postalCode").value("560038"));

        // 4. Pharmacy A submits medicine -> initially PENDING_APPROVAL and NOT visible in public catalog
        MvcResult medResult = mockMvc.perform(post("/api/v1/pharmacy/medicines")
                        .header("Authorization", "Bearer " + pharmacyAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "Augmentin 625 Duo",
                                  "brand": "GSK",
                                  "manufacturer": "GlaxoSmithKline",
                                  "composition": "Amoxicillin 500mg + Clavulanic Acid 125mg",
                                  "dosageForm": "Tablet",
                                  "packSize": "Strip of 10 tablets",
                                  "description": "Broad-spectrum antibiotic.",
                                  "mrpPrice": 220.00,
                                  "sellingPrice": 195.00,
                                  "prescriptionRequired": true
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn();
        Long medicineId = extractLong(medResult.getResponse().getContentAsString(), "\"id\":");

        mockMvc.perform(get("/api/v1/public/medicines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Pharmacy B cannot edit Pharmacy A's medicine
        mockMvc.perform(put("/api/v1/pharmacy/medicines/" + medicineId)
                        .header("Authorization", "Bearer " + pharmacyBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "Hijacked Name",
                                  "brand": "GSK",
                                  "manufacturer": "GlaxoSmithKline",
                                  "composition": "Amoxicillin",
                                  "dosageForm": "Tablet",
                                  "packSize": "10",
                                  "mrpPrice": 220.00,
                                  "sellingPrice": 195.00,
                                  "prescriptionRequired": true
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isNotFound());

        // Upload medicine image
        MockMultipartFile imgFile = new MockMultipartFile(
                "file", "augmentin.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        mockMvc.perform(multipart("/api/v1/pharmacy/medicines/" + medicineId + "/images")
                        .file(imgFile)
                        .param("primary", "true")
                        .header("Authorization", "Bearer " + pharmacyAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.primaryImage").value(true));

        // 5. Admin rejects with reason, then approves after review
        mockMvc.perform(post("/api/v1/admin/medicines/" + medicineId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "rejectionReason": "Clarify pack count"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));

        mockMvc.perform(post("/api/v1/admin/medicines/" + medicineId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        // 6. Add two batches with different expiry dates and verify FEFO + expiry alerts
        LocalDate today = LocalDate.now();
        mockMvc.perform(post("/api/v1/pharmacy/inventory/batches")
                        .header("Authorization", "Bearer " + pharmacyAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "batchNumber": "AUG-LATE",
                                  "manufacturingDate": "%s",
                                  "expiryDate": "%s",
                                  "quantity": 10,
                                  "purchasePrice": 140.00,
                                  "sellingPrice": 195.00
                                }
                                """.formatted(medicineId, today.minusMonths(2), today.plusDays(180))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/pharmacy/inventory/batches")
                        .header("Authorization", "Bearer " + pharmacyAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": %d,
                                  "batchNumber": "AUG-SOON",
                                  "manufacturingDate": "%s",
                                  "expiryDate": "%s",
                                  "quantity": 5,
                                  "purchasePrice": 140.00,
                                  "sellingPrice": 195.00
                                }
                                """.formatted(medicineId, today.minusMonths(6), today.plusDays(20))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/pharmacy/inventory/expiry-alerts?days=45")
                        .header("Authorization", "Bearer " + pharmacyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].batchNumber").value("AUG-SOON"));

        // Public catalog now shows medicine with availableQuantity = 15
        mockMvc.perform(get("/api/v1/public/medicines?q=Augmentin&inStockOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(15));

        // 7. FEFO stock reservation allocates AUG-SOON (5) first, then AUG-LATE (2)
        List<BatchAllocation> allocations = inventoryService.reserveStockFefo(
                "CHK-RES-001", medicineId, 7, Instant.now().plusSeconds(900), 1L
        );
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).batchNumber()).isEqualTo("AUG-SOON");
        assertThat(allocations.get(0).quantityAllocated()).isEqualTo(5);
        assertThat(allocations.get(1).batchNumber()).isEqualTo("AUG-LATE");
        assertThat(allocations.get(1).quantityAllocated()).isEqualTo(2);

        Medicine afterReserve = medicineRepository.findById(medicineId).orElseThrow();
        assertThat(afterReserve.getTotalAvailableQuantity()).isEqualTo(8);

        // Overselling fails cleanly
        assertThatThrownBy(() -> inventoryService.reserveStockFefo(
                "CHK-RES-002", medicineId, 20, Instant.now().plusSeconds(900), 1L
        )).hasMessageContaining("Insufficient non-expired stock");

        // Releasing restores total stock back to 15
        inventoryService.releaseReservations("CHK-RES-001", 1L, "Test cancellation");
        Medicine afterRelease = medicineRepository.findById(medicineId).orElseThrow();
        assertThat(afterRelease.getTotalAvailableQuantity()).isEqualTo(15);

        // 8. Admin suspends Pharmacy A -> its medicines disappear from public catalog immediately
        mockMvc.perform(patch("/api/v1/admin/pharmacies/" + pharmacyAId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "suspended": true,
                                  "reason": "Routine audit hold"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/medicines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
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

    private Long registerPharmacy(String email, String pharmacyName, String licenseNumber) throws Exception {
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
                                  "pharmacistName": "Dr. Pharmacist",
                                  "pharmacistRegistrationNumber": "REG-%s",
                                  "licenseExpiryDate": "%s"
                                }
                                """.formatted(pharmacyName, email, pharmacyName, licenseNumber, licenseNumber,
                                LocalDate.now().plusYears(2))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return pharmacyRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    private void activateEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private void approvePharmacy(String adminToken, Long pharmacyId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/pharmacies/" + pharmacyId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "notes": "Verified drug license"
                                }
                                """))
                .andExpect(status().isOk());
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
