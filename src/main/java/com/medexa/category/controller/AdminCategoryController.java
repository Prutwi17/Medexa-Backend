package com.medexa.category.controller;

import com.medexa.category.dto.CategoryDtos.CategoryRequest;
import com.medexa.category.dto.CategoryDtos.CategoryResponse;
import com.medexa.category.dto.CategoryDtos.CategoryStatusRequest;
import com.medexa.category.service.CategoryService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Categories", description = "Category lifecycle and image management")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories (active and inactive)")
    public ApiResponse<List<CategoryResponse>> listAll() {
        return ApiResponse.ok(categoryService.listAllCategories());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new medicine category")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("Category created.", categoryService.createCategory(request));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Edit an existing medicine category")
    public ApiResponse<CategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("Category updated.", categoryService.updateCategory(categoryId, request));
    }

    @PatchMapping("/{categoryId}/status")
    @Operation(summary = "Activate or deactivate a category")
    public ApiResponse<CategoryResponse> updateStatus(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryStatusRequest request) {
        return ApiResponse.ok(
                "Category status updated.",
                categoryService.setCategoryStatus(categoryId, request.active())
        );
    }

    @PostMapping(value = "/{categoryId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload category image")
    public ApiResponse<CategoryResponse> uploadImage(
            @PathVariable Long categoryId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                "Category image uploaded.",
                categoryService.uploadCategoryImage(categoryId, file)
        );
    }
}
