package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Category category = categoryService.create(request.name());

        URI location = uriComponentsBuilder
                .path("/api/v1/categories/{id}")
                .buildAndExpand(category.getId())
                .toUri();

        return ResponseEntity.created(location).body(CategoryResponse.from(category));
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

}
