package com.farelo.api.ordering;

import java.util.UUID;

public record NewOrderOption(UUID stepId, UUID optionId, int quantity) {
}
