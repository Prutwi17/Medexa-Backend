package com.medexa.inventory.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ConflictException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.inventory.MedicineBatch;
import com.medexa.inventory.MedicineBatchRepository;
import com.medexa.inventory.ReservationStatus;
import com.medexa.inventory.StockMovement;
import com.medexa.inventory.StockMovementRepository;
import com.medexa.inventory.StockMovementType;
import com.medexa.inventory.StockReservation;
import com.medexa.inventory.StockReservationRepository;
import com.medexa.inventory.dto.InventoryDtos.AddBatchRequest;
import com.medexa.inventory.dto.InventoryDtos.AdjustStockRequest;
import com.medexa.inventory.dto.InventoryDtos.BatchAllocation;
import com.medexa.inventory.dto.InventoryDtos.BatchResponse;
import com.medexa.inventory.dto.InventoryDtos.StockMovementResponse;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineRepository;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.service.PharmacyService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final MedicineBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;
    private final MedicineRepository medicineRepository;
    private final PharmacyService pharmacyService;

    @Transactional(readOnly = true)
    public Page<BatchResponse> listBatches(
            Long userId,
            Long medicineId,
            boolean includeExpired,
            Pageable pageable) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        return batchRepository.findByPharmacy(pharmacy.getId(), medicineId, includeExpired, LocalDate.now(), pageable)
                .map(BatchResponse::from);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getExpiryAlerts(Long userId, int withinDays) {
        if (withinDays < 1 || withinDays > 365) {
            throw new BadRequestException("Expiry alert threshold must be between 1 and 365 days.");
        }
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        LocalDate thresholdDate = LocalDate.now().plusDays(withinDays);
        return batchRepository.findExpiringOrExpiredBatches(pharmacy.getId(), thresholdDate).stream()
                .map(BatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(Long userId, Long medicineId, Pageable pageable) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        return movementRepository.findByPharmacy(pharmacy.getId(), medicineId, pageable)
                .map(StockMovementResponse::from);
    }

    @Transactional
    public BatchResponse addBatch(Long userId, AddBatchRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(request.medicineId(), pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found for this pharmacy."));

        if (!request.expiryDate().isAfter(request.manufacturingDate())) {
            throw new BadRequestException("Expiry date must be after manufacturing date.");
        }

        String batchNum = request.batchNumber().trim();
        if (batchRepository.existsByMedicineIdAndBatchNumberIgnoreCase(medicine.getId(), batchNum)) {
            throw new ConflictException("Batch number '" + batchNum + "' already exists for this medicine.");
        }

        MedicineBatch batch = MedicineBatch.builder()
                .medicine(medicine)
                .pharmacy(pharmacy)
                .batchNumber(batchNum)
                .manufacturingDate(request.manufacturingDate())
                .expiryDate(request.expiryDate())
                .initialQuantity(request.quantity())
                .availableQuantity(request.quantity())
                .reservedQuantity(0)
                .purchasePrice(request.purchasePrice())
                .sellingPrice(request.sellingPrice())
                .active(true)
                .build();
        batchRepository.save(batch);

        recordMovement(
                medicine,
                batch,
                pharmacy,
                StockMovementType.BATCH_ADDED,
                request.quantity(),
                batch.getAvailableQuantity(),
                "BATCH",
                batch.getBatchNumber(),
                "Initial batch stock added",
                userId
        );

        syncMedicineTotalStock(medicine);
        return BatchResponse.from(batch);
    }

    @Transactional
    public BatchResponse adjustStock(Long userId, Long batchId, AdjustStockRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(userId);
        MedicineBatch ownedBatch = batchRepository.findByIdAndPharmacyId(batchId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory batch not found."));

        // Acquire pessimistic lock before modifying quantities
        MedicineBatch batch = batchRepository.findByIdForUpdate(ownedBatch.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory batch not found."));

        int delta = request.quantityDelta();
        if (delta == 0) {
            throw new BadRequestException("Quantity delta must be non-zero.");
        }
        int nextAvailable = batch.getAvailableQuantity() + delta;
        if (nextAvailable < 0) {
            throw new BusinessRuleException("Insufficient available quantity in batch for this adjustment.");
        }

        if (request.movementType() == StockMovementType.EXPIRED_WRITE_OFF) {
            if (delta > 0) {
                throw new BadRequestException("Expired write-off quantity delta must be negative.");
            }
            if (nextAvailable == 0 && batch.getReservedQuantity() == 0) {
                batch.setActive(false);
            }
        }

        batch.setAvailableQuantity(nextAvailable);
        batchRepository.save(batch);

        recordMovement(
                batch.getMedicine(),
                batch,
                pharmacy,
                request.movementType(),
                delta,
                nextAvailable,
                "MANUAL_ADJUSTMENT",
                String.valueOf(batch.getId()),
                request.reason().trim(),
                userId
        );

        syncMedicineTotalStock(batch.getMedicine());
        return BatchResponse.from(batch);
    }

    /**
     * Reserves medicine stock across non-expired active batches in First-Expiry-First-Out (FEFO) order
     * using database pessimistic write locks to prevent overselling under concurrency.
     */
    @Transactional
    public List<BatchAllocation> reserveStockFefo(
            String reservationKey,
            Long medicineId,
            int requestedQuantity,
            Instant expiresAt,
            Long actorUserId) {
        if (requestedQuantity <= 0) {
            throw new BadRequestException("Reservation quantity must be positive.");
        }

        Medicine medicine = medicineRepository.findByIdForUpdate(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found."));

        LocalDate today = LocalDate.now();
        List<MedicineBatch> eligibleBatches = batchRepository.findEligibleBatchesForUpdate(medicineId, today);

        int remaining = requestedQuantity;
        List<BatchAllocation> allocations = new ArrayList<>();

        for (MedicineBatch batch : eligibleBatches) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, batch.getAvailableQuantity());
            if (take <= 0) {
                continue;
            }

            batch.setAvailableQuantity(batch.getAvailableQuantity() - take);
            batch.setReservedQuantity(batch.getReservedQuantity() + take);
            batchRepository.save(batch);

            StockReservation reservation = StockReservation.builder()
                    .reservationKey(reservationKey)
                    .medicine(medicine)
                    .batch(batch)
                    .pharmacy(batch.getPharmacy())
                    .quantity(take)
                    .status(ReservationStatus.ACTIVE)
                    .expiresAt(expiresAt)
                    .build();
            reservationRepository.save(reservation);

            recordMovement(
                    medicine,
                    batch,
                    batch.getPharmacy(),
                    StockMovementType.RESERVED,
                    -take,
                    batch.getAvailableQuantity(),
                    "RESERVATION",
                    reservationKey,
                    "FEFO stock reservation",
                    actorUserId
            );

            allocations.add(new BatchAllocation(
                    batch.getId(),
                    batch.getBatchNumber(),
                    batch.getExpiryDate(),
                    take
            ));
            remaining -= take;
        }

        if (remaining > 0) {
            throw new BusinessRuleException(
                    "Insufficient non-expired stock for medicine '" + medicine.getName() + "'."
            );
        }

        syncMedicineTotalStock(medicine);
        return allocations;
    }

    @Transactional
    public void releaseReservations(String reservationKey, Long actorUserId, String reason) {
        List<StockReservation> reservations = reservationRepository.findActiveByReservationKeyForUpdate(reservationKey);
        if (reservations.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Set<Medicine> affectedMedicines = new HashSet<>();

        for (StockReservation reservation : reservations) {
            MedicineBatch batch = batchRepository.findByIdForUpdate(reservation.getBatch().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found during release."));

            int qty = reservation.getQuantity();
            batch.setReservedQuantity(Math.max(0, batch.getReservedQuantity() - qty));
            batch.setAvailableQuantity(batch.getAvailableQuantity() + qty);
            batchRepository.save(batch);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservationRepository.save(reservation);

            recordMovement(
                    reservation.getMedicine(),
                    batch,
                    batch.getPharmacy(),
                    StockMovementType.RELEASED,
                    qty,
                    batch.getAvailableQuantity(),
                    "RESERVATION",
                    reservationKey,
                    reason != null ? reason : "Stock reservation released",
                    actorUserId
            );
            affectedMedicines.add(reservation.getMedicine());
        }

        affectedMedicines.forEach(this::syncMedicineTotalStock);
    }

    @Transactional
    public void commitReservations(String reservationKey, Long actorUserId) {
        List<StockReservation> reservations = reservationRepository.findActiveByReservationKeyForUpdate(reservationKey);
        if (reservations.isEmpty()) {
            throw new BusinessRuleException("No active stock reservation found for key '" + reservationKey + "'.");
        }

        for (StockReservation reservation : reservations) {
            MedicineBatch batch = batchRepository.findByIdForUpdate(reservation.getBatch().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found during commit."));

            int qty = reservation.getQuantity();
            batch.setReservedQuantity(Math.max(0, batch.getReservedQuantity() - qty));
            batchRepository.save(batch);

            reservation.setStatus(ReservationStatus.COMMITTED);
            reservationRepository.save(reservation);

            recordMovement(
                    reservation.getMedicine(),
                    batch,
                    batch.getPharmacy(),
                    StockMovementType.DISPATCHED,
                    0,
                    batch.getAvailableQuantity(),
                    "RESERVATION",
                    reservationKey,
                    "Reserved stock committed for dispatch",
                    actorUserId
            );
        }
    }

    private void syncMedicineTotalStock(Medicine medicine) {
        Integer total = batchRepository.sumSellableQuantity(medicine.getId(), LocalDate.now());
        medicine.setTotalAvailableQuantity(total != null ? total : 0);
        medicineRepository.save(medicine);
    }

    private void recordMovement(
            Medicine medicine,
            MedicineBatch batch,
            Pharmacy pharmacy,
            StockMovementType type,
            int delta,
            int balanceAfter,
            String refType,
            String refId,
            String reason,
            Long actorUserId) {
        StockMovement movement = StockMovement.builder()
                .medicine(medicine)
                .batch(batch)
                .pharmacy(pharmacy)
                .movementType(type)
                .quantityDelta(delta)
                .balanceAfter(balanceAfter)
                .referenceType(refType)
                .referenceId(refId)
                .reason(reason)
                .actorUserId(actorUserId)
                .build();
        movementRepository.save(movement);
    }
}
