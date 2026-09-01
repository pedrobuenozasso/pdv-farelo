package com.farelo.api.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product create(String name, String description, BigDecimal price, UUID categoryId, String imageUrl) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        Product product = new Product(name, price, category);
        product.setDescription(description);
        product.setImageUrl(imageUrl);

        return productRepository.save(product);
    }

    public List<Product> listAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

}
