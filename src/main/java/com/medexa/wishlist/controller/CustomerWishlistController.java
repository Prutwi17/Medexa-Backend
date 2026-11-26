package com.medexa.wishlist.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.security.MedexaUserPrincipal;
import com.medexa.wishlist.service.WishlistService;
import com.medexa.wishlist.service.WishlistService.WishlistEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/wishlist")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Wishlist", description = "Saved medicines wishlist management")
public class CustomerWishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @Operation(summary = "List authenticated customer's saved medicines")
    public ApiResponse<List<WishlistEntryResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(wishlistService.listWishlist(principal.getUserId()));
    }

    @PostMapping("/{medicineId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a medicine to authenticated customer's wishlist")
    public ApiResponse<WishlistEntryResponse> add(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId) {
        return ApiResponse.ok("Added to wishlist.", wishlistService.addToWishlist(principal.getUserId(), medicineId));
    }

    @DeleteMapping("/{medicineId}")
    @Operation(summary = "Remove a medicine from authenticated customer's wishlist")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId) {
        wishlistService.removeFromWishlist(principal.getUserId(), medicineId);
        return ApiResponse.ok("Removed from wishlist.", null);
    }
}
