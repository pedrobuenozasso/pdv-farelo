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

    public Category create(String name) {
        Category category = new Category(name);
        return categoryRepository.save(category);
    }

    public List<Category> listAll() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

}
