package com.farelo.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductStepRepository extends JpaRepository<ProductStep, UUID> {
    List<ProductStep> findByProductIdOrderByDisplayOrderAsc(UUID productId);
}
