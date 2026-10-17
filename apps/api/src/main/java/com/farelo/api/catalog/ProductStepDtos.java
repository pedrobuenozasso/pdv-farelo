package com.farelo.api.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Admin-only request/response shapes; never serialize JPA entities. */
public final class ProductStepDtos {
    private ProductStepDtos() { }

    public record ConfigurationRequest(@NotNull List<@NotNull @Valid StepRequest> steps) { }

    public record StepRequest(
            @NotBlank @Size(max = 120) String label,
            @NotNull @Min(0) Integer minChoices,
            @NotNull @Min(0) Integer maxChoices,
            @NotNull @Min(0) Integer displayOrder,
            @NotBlank @Size(max = 80) String level,
            @NotBlank @Size(max = 40) String chargeType,
            @NotNull List<@NotNull @Valid OptionRequest> options) { }

    public record OptionRequest(
            @NotNull UUID optionProductId,
            @NotNull @Min(0) Integer displayOrder,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) { }

    public record StepResponse(
            UUID id, String label, int minChoices, int maxChoices, int displayOrder,
            String level, String chargeType, List<OptionResponse> options) { }

    public record OptionResponse(
            UUID id, UUID optionProductId, String optionName, boolean optionActive,
            int displayOrder, BigDecimal price) { }
}
