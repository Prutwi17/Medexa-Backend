package com.medexa.cart.controller;

import com.medexa.cart.dto.CartDtos.AddToCartRequest;
import com.medexa.cart.dto.CartDtos.CartSummaryResponse;
import com.medexa.cart.dto.CartDtos.UpdateCartItemRequest;
import com.medexa.cart.service.CartService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/cart")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Cart", description = "Multi-pharmacy customer shopping cart management")
public class CustomerCartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get cart grouped by pharmacy with server-computed pricing and dispatch readiness")
    public ApiResponse<CartSummaryResponse> getCart(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) Long addressId) {
        return ApiResponse.ok(cartService.getCart(principal.getUserId(), addressId));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a medicine to the cart")
    public ApiResponse<CartSummaryResponse> addItem(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody AddToCartRequest request,
            @RequestParam(required = false) Long addressId) {
        return ApiResponse.ok("Medicine added to cart.", cartService.addItem(principal.getUserId(), request, addressId));
    }

    @PutMapping("/items/{cartItemId}")
    @Operation(summary = "Update cart item quantity or attached prescription")
    public ApiResponse<CartSummaryResponse> updateItem(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @RequestParam(required = false) Long addressId) {
        return ApiResponse.ok(
                "Cart item updated.",
                cartService.updateItem(principal.getUserId(), cartItemId, request, addressId)
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(summary = "Remove a medicine from the cart")
    public ApiResponse<CartSummaryResponse> removeItem(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long cartItemId,
            @RequestParam(required = false) Long addressId) {
        return ApiResponse.ok(
                "Item removed from cart.",
                cartService.removeItem(principal.getUserId(), cartItemId, addressId)
        );
    }

    @DeleteMapping
    @Operation(summary = "Clear all items in the authenticated customer's cart")
    public ApiResponse<CartSummaryResponse> clearCart(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok("Cart cleared.", cartService.clearCart(principal.getUserId()));
    }
}
