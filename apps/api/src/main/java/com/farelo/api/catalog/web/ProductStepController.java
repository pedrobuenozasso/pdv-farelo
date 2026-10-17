package com.farelo.api.catalog.web;

import com.farelo.api.catalog.ProductStepService;
import com.farelo.api.catalog.ProductStepDtos.ConfigurationRequest;
import com.farelo.api.catalog.ProductStepDtos.StepResponse;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Staff configuration read for PDV; only Admin/Manager may replace draft steps. */
@RestController
@RequestMapping("/api/v1/products/{productId}/steps")
@RequireRole({UserRole.ADMIN, UserRole.MANAGER})
public class ProductStepController {
    private final ProductStepService service;

    public ProductStepController(ProductStepService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public List<StepResponse> list(@PathVariable UUID productId) {
        return service.list(productId);
    }

    @PutMapping
    public List<StepResponse> replace(@PathVariable UUID productId,
                                      @Valid @RequestBody ConfigurationRequest request) {
        return service.replace(productId, request.steps());
    }
}
