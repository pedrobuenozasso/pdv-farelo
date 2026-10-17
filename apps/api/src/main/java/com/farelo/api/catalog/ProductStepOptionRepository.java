package com.farelo.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductStepOptionRepository extends JpaRepository<ProductStepOption, UUID> {
    List<ProductStepOption> findByStepIdOrderByDisplayOrderAsc(UUID stepId);
}
