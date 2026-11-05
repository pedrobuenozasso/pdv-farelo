package com.farelo.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductStepRepository extends JpaRepository<ProductStep, UUID> {
    List<ProductStep> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    /**
     * IDs de todos os produtos com pelo menos um passo configurado — uma
     * consulta só para a listagem inteira de produtos (campo {@code hasSteps}
     * de {@code GET /api/v1/products}), sem N+1. {@code s.product.id} lê a
     * FK {@code product_step.product_id} direto, sem join em {@code product}.
     */
    @Query("select distinct s.product.id from ProductStep s")
    List<UUID> findDistinctProductIds();

    boolean existsByProductId(UUID productId);
}
