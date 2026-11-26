package com.medexa.inventory.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.inventory.dto.InventoryDtos.AddBatchRequest;
import com.medexa.inventory.dto.InventoryDtos.AdjustStockRequest;
import com.medexa.inventory.dto.InventoryDtos.BatchResponse;
import com.medexa.inventory.dto.InventoryDtos.StockMovementResponse;
import com.medexa.inventory.service.InventoryService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Pharmacy Inventory", description = "Batch inventory, expiry alerts, and stock movement history")
public class PharmacyInventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/batches")
    @Operation(summary = "List pharmacy medicine batches")
    public ApiResponse<List<BatchResponse>> listBatches(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) Long medicineId,
            @RequestParam(defaultValue = "false") boolean includeExpired,
            Pageable pageable) {
        Page<BatchResponse> page = inventoryService.listBatches(
                principal.getUserId(), medicineId, includeExpired, pageable
        );
        return ApiResponse.paged("Inventory batches fetched.", page);
    }

    @GetMapping("/expiry-alerts")
    @Operation(summary = "List batches expiring within N days or already expired with stock")
    public ApiResponse<List<BatchResponse>> expiryAlerts(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(defaultValue = "60") int days) {
        return ApiResponse.ok(inventoryService.getExpiryAlerts(principal.getUserId(), days));
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new medicine batch to inventory")
    public ApiResponse<BatchResponse> addBatch(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody AddBatchRequest request) {
        return ApiResponse.ok(
                "Batch added to inventory.",
                inventoryService.addBatch(principal.getUserId(), request)
        );
    }

    @PatchMapping("/batches/{batchId}/adjust")
    @Operation(summary = "Adjust available batch quantity or write off expired stock")
    public ApiResponse<BatchResponse> adjustStock(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long batchId,
            @Valid @RequestBody AdjustStockRequest request) {
        return ApiResponse.ok(
                "Batch inventory updated.",
                inventoryService.adjustStock(principal.getUserId(), batchId, request)
        );
    }

    @GetMapping("/movements")
    @Operation(summary = "List stock movement history")
    public ApiResponse<List<StockMovementResponse>> listMovements(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) Long medicineId,
            Pageable pageable) {
        Page<StockMovementResponse> page = inventoryService.listMovements(
                principal.getUserId(), medicineId, pageable
        );
        return ApiResponse.paged("Stock movements fetched.", page);
    }
}
