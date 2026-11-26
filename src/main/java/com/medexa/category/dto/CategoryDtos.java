package com.medexa.category.dto;

import com.medexa.category.Category;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Category name is required.")
            @Size(min = 2, max = 120)
            String name,

            @Size(max = 140)
            String slug,

            @Size(max = 500)
            String description,

            @Size(max = 500)
            String imageUrl,

            @Min(0)
            Integer displayOrder
    ) {
    }

    public record CategoryStatusRequest(@NotNull Boolean active) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String slug,
            String description,
            String imageUrl,
            Integer displayOrder,
            boolean active
    ) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getSlug(),
                    category.getDescription(),
                    category.getImageUrl(),
                    category.getDisplayOrder(),
                    category.isActive()
            );
        }
    }
}
