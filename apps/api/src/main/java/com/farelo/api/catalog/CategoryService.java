package com.farelo.api.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // FARELO-261: description/sortOrder — null sortOrder defaults to 0
    // (Category's own field default), same "absent means default"
    // reasoning CategoryRequest's javadoc documents.
    public Category create(String name, String description, Integer sortOrder) {
        Category category = new Category(name);
        category.setDescription(description);
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }
        return categoryRepository.save(category);
    }

    // FARELO-261: sortOrder first (categories with an explicit lower order
    // come first), then name as the stable tiebreaker for everything else
    // still at the shared default.
    public List<Category> listAll() {
        return categoryRepository.findAll(
                Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "name")));
    }

}
