package com.medexa.cart.service;

import com.medexa.address.CustomerAddress;
import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItem;
import com.medexa.cart.CartItemRepository;
import com.medexa.cart.dto.CartDtos.AddToCartRequest;
import com.medexa.cart.dto.CartDtos.CartItemResponse;
import com.medexa.cart.dto.CartDtos.CartSummaryResponse;
import com.medexa.cart.dto.CartDtos.PharmacyCartGroupResponse;
import com.medexa.cart.dto.CartDtos.UpdateCartItemRequest;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineImage;
import com.medexa.medicine.MedicineImageRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.medicine.service.MedicineClassificationService;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import com.medexa.pharmacy.PharmacyDeliveryZoneRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.prescription.Prescription;
import com.medexa.prescription.dto.PrescriptionDtos.DispatchEligibilityResponse;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineImageRepository medicineImageRepository;
    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;
    private final PharmacyDeliveryZoneRepository deliveryZoneRepository;
    private final MedicineClassificationService classificationService;
    private final PrescriptionService prescriptionService;

    @Transactional
    public CartSummaryResponse getCart(Long customerId, Long addressId) {
        return buildCartSummary(customerId, addressId);
    }

    @Transactional
    public CartSummaryResponse addItem(Long customerId, AddToCartRequest request, Long addressId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        Medicine medicine = medicineRepository.findById(request.medicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));

        validateMedicineOrderable(medicine);
        MedicineClassificationRule rule = classificationService.resolveRule(
                medicine.getClassificationCode(),
                medicine.isPrescriptionRequired()
        );

        Optional<CartItem> existingOpt = cartItemRepository
                .findByCustomerIdAndMedicineId(customerId, medicine.getId());
        int targetQuantity = existingOpt
                .map(existing -> existing.getQuantity() + request.quantity())
                .orElse(request.quantity());

        validateQuantityLimits(medicine, rule, targetQuantity);

        Prescription attachedPrescription = existingOpt.map(CartItem::getPrescription).orElse(null);
        if (request.prescriptionId() != null) {
            attachedPrescription = prescriptionService.requireOwnedPrescription(customerId, request.prescriptionId());
            if (attachedPrescription.getPharmacy() != null
                    && !attachedPrescription.getPharmacy().getId().equals(medicine.getPharmacy().getId())) {
                throw new BusinessRuleException("Selected prescription belongs to a different pharmacy.");
            }
        }

        CartItem item = existingOpt.orElseGet(() -> CartItem.builder()
                .customer(customer)
                .medicine(medicine)
                .pharmacy(medicine.getPharmacy())
                .build());
        item.setQuantity(targetQuantity);
        item.setPrescription(attachedPrescription);
        cartItemRepository.save(item);

        return buildCartSummary(customerId, addressId);
    }

    @Transactional
    public CartSummaryResponse updateItem(
            Long customerId,
            Long cartItemId,
            UpdateCartItemRequest request,
            Long addressId) {
        CartItem item = cartItemRepository.findByIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        Medicine medicine = item.getMedicine();

        validateMedicineOrderable(medicine);
        MedicineClassificationRule rule = classificationService.resolveRule(
                medicine.getClassificationCode(),
                medicine.isPrescriptionRequired()
        );
        validateQuantityLimits(medicine, rule, request.quantity());

        item.setQuantity(request.quantity());
        if (Boolean.TRUE.equals(request.clearPrescription())) {
            item.setPrescription(null);
        } else if (request.prescriptionId() != null) {
            Prescription rx = prescriptionService.requireOwnedPrescription(customerId, request.prescriptionId());
            if (rx.getPharmacy() != null
                    && !rx.getPharmacy().getId().equals(medicine.getPharmacy().getId())) {
                throw new BusinessRuleException("Selected prescription belongs to a different pharmacy.");
            }
            item.setPrescription(rx);
        }

        cartItemRepository.save(item);
        return buildCartSummary(customerId, addressId);
    }

    @Transactional
    public CartSummaryResponse removeItem(Long customerId, Long cartItemId, Long addressId) {
        CartItem item = cartItemRepository.findByIdAndCustomerId(cartItemId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        cartItemRepository.delete(item);
        cartItemRepository.flush();
        return buildCartSummary(customerId, addressId);
    }

    @Transactional
    public CartSummaryResponse clearCart(Long customerId) {
        cartItemRepository.deleteByCustomerId(customerId);
        cartItemRepository.flush();
        return buildCartSummary(customerId, null);
    }

    private CartSummaryResponse buildCartSummary(Long customerId, Long requestedAddressId) {
        List<CartItem> items = cartItemRepository.findAllForCustomer(customerId);
        CustomerAddress selectedAddress = resolveSelectedAddress(customerId, requestedAddressId);
        String postalCode = selectedAddress != null ? selectedAddress.getPostalCode() : null;

        if (items.isEmpty()) {
            return new CartSummaryResponse(
                    selectedAddress != null ? selectedAddress.getId() : null,
                    postalCode,
                    0,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false,
                    List.of(),
                    List.of()
            );
        }

        List<Long> medicineIds = items.stream().map(i -> i.getMedicine().getId()).toList();
        Map<Long, String> primaryImageByMedicine = loadPrimaryImageMap(medicineIds);
        Map<String, MedicineClassificationRule> ruleMap = classificationService.loadRuleMap();

        Map<Long, List<CartItem>> byPharmacy = new LinkedHashMap<>();
        for (CartItem item : items) {
            byPharmacy.computeIfAbsent(item.getPharmacy().getId(), k -> new ArrayList<>()).add(item);
        }

        List<PharmacyCartGroupResponse> groups = new ArrayList<>();
        List<String> globalBlockers = new ArrayList<>();
        BigDecimal totalItemsSubtotal = BigDecimal.ZERO;
        BigDecimal totalDeliveryFee = BigDecimal.ZERO;
        int totalUnits = 0;
        boolean allGroupsReady = true;

        for (List<CartItem> pharmacyItems : byPharmacy.values()) {
            Pharmacy pharmacy = pharmacyItems.get(0).getPharmacy();
            List<CartItemResponse> itemDtos = new ArrayList<>();
            List<String> groupBlockers = new ArrayList<>();
            BigDecimal groupSubtotal = BigDecimal.ZERO;

            for (CartItem ci : pharmacyItems) {
                Medicine med = ci.getMedicine();
                String code = med.getClassificationCode() != null ? med.getClassificationCode().toUpperCase() : "OTC";
                MedicineClassificationRule rule = ruleMap.get(code);
                boolean rxRequired = med.isPrescriptionRequired() || (rule != null && rule.isPrescriptionRequired());
                int maxUnits = rule != null ? rule.getMaxUnitsPerOrder() : (rxRequired ? 30 : 50);
                int available = med.getTotalAvailableQuantity() != null ? med.getTotalAvailableQuantity() : 0;

                String itemBlocker = null;
                boolean dispatchEligible = true;

                if (!isMedicineActiveAndApproved(med)) {
                    itemBlocker = med.getName() + " is no longer available in the verified catalog.";
                    dispatchEligible = false;
                } else if (available < ci.getQuantity()) {
                    itemBlocker = med.getName() + " has only " + available + " unit(s) remaining in stock.";
                    dispatchEligible = false;
                } else if (rxRequired) {
                    Long rxId = ci.getPrescription() != null ? ci.getPrescription().getId() : null;
                    DispatchEligibilityResponse rxCheck = prescriptionService
                            .checkDispatchEligibility(customerId, med.getId(), rxId, false);
                    dispatchEligible = rxCheck.eligible();
                    if (!rxCheck.eligible()) {
                        itemBlocker = rxCheck.reason();
                    }
                }

                if (itemBlocker != null) {
                    groupBlockers.add(itemBlocker);
                }

                BigDecimal lineTotal = med.getSellingPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                groupSubtotal = groupSubtotal.add(lineTotal);
                totalUnits += ci.getQuantity();

                itemDtos.add(new CartItemResponse(
                        ci.getId(),
                        med.getId(),
                        med.getName(),
                        med.getSlug(),
                        med.getBrand(),
                        med.getPackSize(),
                        primaryImageByMedicine.get(med.getId()),
                        ci.getQuantity(),
                        available,
                        available > 0,
                        maxUnits,
                        med.getMrpPrice(),
                        med.getSellingPrice(),
                        lineTotal,
                        rxRequired,
                        code,
                        ci.getPrescription() != null ? ci.getPrescription().getId() : null,
                        ci.getPrescription() != null ? ci.getPrescription().getReferenceCode() : null,
                        ci.getPrescription() != null ? ci.getPrescription().getStatus() : null,
                        dispatchEligible,
                        itemBlocker
                ));
            }

            Boolean deliverable = null;
            BigDecimal deliveryFee = BigDecimal.ZERO;
            BigDecimal minOrderAmount = BigDecimal.ZERO;
            Integer etaMinutes = null;

            if (postalCode != null) {
                Optional<PharmacyDeliveryZone> zoneOpt = deliveryZoneRepository
                        .findActiveZoneForPharmacyAndPostalCode(pharmacy.getId(), postalCode);
                if (zoneOpt.isPresent()) {
                    PharmacyDeliveryZone zone = zoneOpt.get();
                    deliverable = true;
                    deliveryFee = zone.getDeliveryFee();
                    minOrderAmount = zone.getMinOrderAmount();
                    etaMinutes = zone.getEstimatedDeliveryMinutes();
                    if (groupSubtotal.compareTo(minOrderAmount) < 0) {
                        groupBlockers.add(
                                pharmacy.getPharmacyName() + " requires a minimum order of ₹"
                                        + minOrderAmount.toPlainString() + " for postal code " + postalCode + "."
                        );
                    }
                } else {
                    deliverable = false;
                    groupBlockers.add(
                            pharmacy.getPharmacyName() + " does not deliver to postal code " + postalCode + "."
                    );
                }
            }

            boolean groupReady = groupBlockers.isEmpty();
            if (!groupReady) {
                allGroupsReady = false;
                globalBlockers.addAll(groupBlockers);
            }

            BigDecimal groupTotal = groupSubtotal.add(deliveryFee);
            totalItemsSubtotal = totalItemsSubtotal.add(groupSubtotal);
            totalDeliveryFee = totalDeliveryFee.add(deliveryFee);

            groups.add(new PharmacyCartGroupResponse(
                    pharmacy.getId(),
                    pharmacy.getPharmacyName(),
                    pharmacy.getSlug(),
                    pharmacy.getCity(),
                    itemDtos,
                    groupSubtotal,
                    deliverable,
                    deliveryFee,
                    minOrderAmount,
                    etaMinutes,
                    groupTotal,
                    groupReady,
                    groupBlockers
            ));
        }

        if (postalCode == null) {
            globalBlockers.add("Select or add a delivery address to validate pharmacy zone serviceability.");
        }

        return new CartSummaryResponse(
                selectedAddress != null ? selectedAddress.getId() : null,
                postalCode,
                totalUnits,
                groups.size(),
                totalItemsSubtotal,
                totalDeliveryFee,
                totalItemsSubtotal.add(totalDeliveryFee),
                allGroupsReady && postalCode != null,
                globalBlockers,
                groups
        );
    }

    private CustomerAddress resolveSelectedAddress(Long customerId, Long requestedAddressId) {
        if (requestedAddressId != null) {
            return addressRepository.findByIdAndCustomerId(requestedAddressId, customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Selected delivery address not found."));
        }
        return addressRepository.findByCustomerIdAndDefaultAddressTrue(customerId).orElse(null);
    }

    private void validateMedicineOrderable(Medicine medicine) {
        if (!isMedicineActiveAndApproved(medicine)) {
            throw new BusinessRuleException("This medicine is not available from an active verified pharmacy.");
        }
    }

    private boolean isMedicineActiveAndApproved(Medicine m) {
        return m.getApprovalStatus() == MedicineApprovalStatus.APPROVED
                && m.isActive()
                && m.getCategory().isActive()
                && m.getPharmacy().getVerificationStatus() == VerificationStatus.APPROVED
                && m.getPharmacy().getUser().getAccountStatus() == AccountStatus.ACTIVE;
    }

    private void validateQuantityLimits(
            Medicine medicine,
            MedicineClassificationRule rule,
            int targetQuantity) {
        if (targetQuantity < 1) {
            throw new BadRequestException("Quantity must be at least 1.");
        }
        int available = medicine.getTotalAvailableQuantity() != null ? medicine.getTotalAvailableQuantity() : 0;
        if (available < targetQuantity) {
            throw new BusinessRuleException(
                    "Only " + available + " unit(s) of " + medicine.getName() + " are currently available."
            );
        }
        int maxAllowed = rule.getMaxUnitsPerOrder() != null ? rule.getMaxUnitsPerOrder() : 50;
        if (targetQuantity > maxAllowed) {
            throw new BusinessRuleException(
                    "Maximum " + maxAllowed + " unit(s) permitted per order for " + rule.getDisplayName() + "."
            );
        }
    }

    private Map<Long, String> loadPrimaryImageMap(List<Long> medicineIds) {
        if (medicineIds.isEmpty()) {
            return Map.of();
        }
        return medicineImageRepository
                .findByMedicineIdInOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicineIds)
                .stream()
                .collect(Collectors.toMap(
                        img -> img.getMedicine().getId(),
                        MedicineImage::getImageUrl,
                        (first, ignored) -> first
                ));
    }
}
