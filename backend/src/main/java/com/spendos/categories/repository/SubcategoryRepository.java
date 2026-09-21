package com.spendos.categories.repository;

import com.spendos.categories.domain.Subcategory;
import com.spendos.common.repository.BaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubcategoryRepository extends BaseRepository<Subcategory> {
    List<Subcategory> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<Subcategory> findByCategoryIdAndSubcategoryNameIgnoreCase(UUID categoryId, String subcategoryName);
}
