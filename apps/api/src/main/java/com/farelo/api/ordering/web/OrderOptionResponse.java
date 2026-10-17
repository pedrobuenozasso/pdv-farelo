package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderItemOption;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderOptionResponse(UUID stepId, String stepName, UUID optionId,
                                  UUID optionProductId, String optionName, int quantity,
                                  BigDecimal unitPrice) {
    public static OrderOptionResponse from(OrderItemOption option) {
        return new OrderOptionResponse(option.getStepId(), option.getStepName(),
                option.getOptionId(), option.getOptionProductId(), option.getOptionName(),
                option.getQuantity(), option.getUnitPrice());
    }
}
