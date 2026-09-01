package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductService;
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
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Product product = productService.create(
                request.name(),
                request.description(),
                request.price(),
                request.categoryId(),
                request.imageUrl());

        URI location = uriComponentsBuilder
                .path("/api/v1/products/{id}")
                .buildAndExpand(product.getId())
                .toUri();

        return ResponseEntity.created(location).body(ProductResponse.from(product));
    }

    // No categoryId (or other) filter yet — YAGNI, no consumer (Admin/PDV)
    // asking for it yet. Add a query param here if/when one does.
    @GetMapping
    public List<ProductResponse> list() {
        return productService.listAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

}
