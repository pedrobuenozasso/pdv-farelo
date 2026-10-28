package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalNcmReviewService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fiscal-ncm-reviews")
public class FiscalNcmReviewController {

    private final FiscalNcmReviewService service;

    public FiscalNcmReviewController(FiscalNcmReviewService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole(UserRole.ADMIN)
    public List<FiscalNcmReviewService.Review> list() {
        return service.list();
    }

    @PutMapping("/{productId}")
    @RequireRole(UserRole.ADMIN)
    public FiscalNcmReviewService.Review save(
            @PathVariable UUID productId, @Valid @RequestBody UpdateRequest request) {
        return service.save(productId, request.draftNcm(), request.reviewed());
    }

    public record UpdateRequest(
            @Pattern(regexp = "^[0-9]{8}$", message = "NCM must have 8 digits") String draftNcm,
            boolean reviewed) {
        @AssertTrue(message = "reviewed NCM requires an 8-digit code")
        public boolean isReviewValid() {
            return !reviewed || draftNcm != null;
        }
    }
}
