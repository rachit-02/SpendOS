package com.spendos.categories.dto;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(UUID id, String categoryName, String iconName, String colorHex, Integer displayOrder,
                               boolean isSystem, List<SubcategoryResponse> subcategories) {

    public record SubcategoryResponse(UUID id, String subcategoryName, Integer displayOrder) {
    }
}
