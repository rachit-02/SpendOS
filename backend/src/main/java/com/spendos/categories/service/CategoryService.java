package com.spendos.categories.service;

import com.spendos.categories.domain.Category;
import com.spendos.categories.domain.Subcategory;
import com.spendos.categories.dto.CategoryResponse;
import com.spendos.categories.dto.CategoryResponse.SubcategoryResponse;
import com.spendos.categories.repository.CategoryRepository;
import com.spendos.categories.repository.SubcategoryRepository;
import com.spendos.common.exception.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** System categories are static reference data, so lookups are cached. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public CategoryService(CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        Map<UUID, List<Subcategory>> subcategories = subcategoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().collect(Collectors.groupingBy(Subcategory::getCategoryId));
        return categoryRepository.findAllByOrderByDisplayOrderAscCategoryNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getCategoryName(),
                        category.getIconName(), category.getColorHex(), category.getDisplayOrder(), category.isSystem(),
                        subcategories.getOrDefault(category.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Subcategory::getDisplayOrder,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(s -> new SubcategoryResponse(s.getId(), s.getSubcategoryName(), s.getDisplayOrder()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Category require(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_REQUEST", "Category not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Category> findByName(String name) {
        return categoryRepository.findByCategoryNameIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public Category other() {
        return categoryRepository.findByCategoryNameIgnoreCase(Category.OTHER)
                .orElseThrow(() -> new IllegalStateException("Seed category 'Other' is missing"));
    }

    /** Verifies the subcategory exists and belongs to the category. */
    @Transactional(readOnly = true)
    public void requireSubcategoryOf(UUID subcategoryId, UUID categoryId) {
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_REQUEST", "Subcategory not found"));
        if (!subcategory.getCategoryId().equals(categoryId)) {
            throw ApiException.badRequest("INVALID_REQUEST", "Subcategory does not belong to the category");
        }
    }
}
