package com.farelo.api.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Product create(
            String name, String description, BigDecimal price, UUID categoryId, String imageUrl,
            Boolean availableOnMenu, Boolean availableOnPos) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        Product product = new Product(name, price, category);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        // availableOnMenu/availableOnPos default to true (see Product's field
        // initializers) when the request omits them — only override when the
        // caller explicitly sent a value.
        if (availableOnMenu != null) {
            product.setAvailableOnMenu(availableOnMenu);
        }
        if (availableOnPos != null) {
            product.setAvailableOnPos(availableOnPos);
        }

        return productRepository.save(product);
    }

    public List<Product> listAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by both update() below and com.farelo.api.ordering.OrderService
    // (order creation needs to fetch a product by id, same 404 semantics).
    public Product getById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product update(
            UUID id, String name, String description, BigDecimal price, UUID categoryId, String imageUrl,
            boolean active, boolean availableOnMenu, boolean availableOnPos) {
        Product product = getById(id);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        product.setActive(active);
        product.setAvailableOnMenu(availableOnMenu);
        product.setAvailableOnPos(availableOnPos);

        return productRepository.save(product);
    }

}
