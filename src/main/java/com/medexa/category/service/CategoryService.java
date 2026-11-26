package com.medexa.category.service;

import com.medexa.category.Category;
import com.medexa.category.CategoryRepository;
import com.medexa.category.dto.CategoryDtos.CategoryRequest;
import com.medexa.category.dto.CategoryDtos.CategoryResponse;
import com.medexa.common.exception.ConflictException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.common.util.SlugUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category with name '" + name + "' already exists.");
        }

        String requestedSlug = request.slug() != null && !request.slug().isBlank()
                ? SlugUtils.toSlug(request.slug(), "category")
                : SlugUtils.toSlug(name, "category");

        if (categoryRepository.existsBySlugIgnoreCase(requestedSlug)) {
            throw new ConflictException("Category slug '" + requestedSlug + "' is already in use.");
        }

        Category category = Category.builder()
                .name(name)
                .slug(requestedSlug)
                .description(trimOrNull(request.description()))
                .imageUrl(trimOrNull(request.imageUrl()))
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .active(true)
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));

        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, categoryId)) {
            throw new ConflictException("Another category already uses name '" + name + "'.");
        }

        String slug = request.slug() != null && !request.slug().isBlank()
                ? SlugUtils.toSlug(request.slug(), "category")
                : SlugUtils.toSlug(name, "category");

        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, categoryId)) {
            throw new ConflictException("Another category already uses slug '" + slug + "'.");
        }

        category.setName(name);
        category.setSlug(slug);
        category.setDescription(trimOrNull(request.description()));
        if (request.imageUrl() != null) {
            category.setImageUrl(trimOrNull(request.imageUrl()));
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse setCategoryStatus(Long categoryId, boolean active) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        category.setActive(active);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse uploadCategoryImage(Long categoryId, MultipartFile file) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        String imageUrl = fileStorageService.storeImage(FileStorageService.BUCKET_CATEGORIES, file);
        category.setImageUrl(imageUrl);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    private String trimOrNull(String val) {
        return val == null || val.isBlank() ? null : val.trim();
    }
}
