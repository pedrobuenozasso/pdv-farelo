package com.farelo.api.ordering.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record OrderOptionRequest(@NotNull UUID stepId, @NotNull UUID optionId,
                                 @Positive int quantity) {
}
