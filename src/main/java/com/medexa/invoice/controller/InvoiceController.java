package com.medexa.invoice.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.invoice.dto.InvoiceDtos.InvoiceResponse;
import com.medexa.invoice.service.InvoiceService;
import com.medexa.invoice.service.InvoiceService.RenderedInvoicePdf;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Invoices", description = "Downloadable PDF and JSON invoices for paid pharmacy orders")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/api/v1/customer/orders/{orderId}/invoice")
    @Operation(summary = "Get invoice summary for an owned paid order")
    public ApiResponse<InvoiceResponse> getCustomerInvoice(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(invoiceService.getCustomerInvoice(principal.getUserId(), orderId));
    }

    @GetMapping("/api/v1/customer/orders/{orderId}/invoice/pdf")
    @Operation(summary = "Download PDF invoice for an owned paid order")
    public ResponseEntity<byte[]> downloadCustomerInvoicePdf(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return pdfResponse(invoiceService.downloadCustomerInvoicePdf(principal.getUserId(), orderId));
    }

    @GetMapping("/api/v1/pharmacy/orders/{orderId}/invoice/pdf")
    @Operation(summary = "Download PDF invoice for a dispensing pharmacy order")
    public ResponseEntity<byte[]> downloadPharmacyInvoicePdf(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return pdfResponse(invoiceService.downloadPharmacyInvoicePdf(principal.getUserId(), orderId));
    }

    @GetMapping("/api/v1/admin/orders/{orderId}/invoice/pdf")
    @Operation(summary = "Download PDF invoice for any paid platform order")
    public ResponseEntity<byte[]> downloadAdminInvoicePdf(@PathVariable Long orderId) {
        return pdfResponse(invoiceService.downloadAdminInvoicePdf(orderId));
    }

    private ResponseEntity<byte[]> pdfResponse(RenderedInvoicePdf payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(payload.filename()).build().toString()
                )
                .body(payload.bytes());
    }
}
