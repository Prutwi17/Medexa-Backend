package com.medexa.category.controller;

import com.medexa.category.dto.CategoryDtos.CategoryResponse;
import com.medexa.category.service.CategoryService;
import com.medexa.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/categories")
@RequiredArgsConstructor
@Tag(name = "Public Categories", description = "Active medicine categories")
public class PublicCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List active medicine categories")
    public ApiResponse<List<CategoryResponse>> listActive() {
        return ApiResponse.ok(categoryService.listActiveCategories());
    }
}
