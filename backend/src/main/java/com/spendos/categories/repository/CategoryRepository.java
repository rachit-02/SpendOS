package com.spendos.categories.repository;

import com.spendos.categories.domain.Category;
import com.spendos.common.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends BaseRepository<Category> {
    Optional<Category> findByCategoryNameIgnoreCase(String categoryName);

    List<Category> findAllByOrderByDisplayOrderAscCategoryNameAsc();
}
