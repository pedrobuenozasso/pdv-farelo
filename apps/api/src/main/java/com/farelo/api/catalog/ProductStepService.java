package com.farelo.api.catalog;

import com.farelo.api.catalog.ProductStepDtos.OptionRequest;
import com.farelo.api.catalog.ProductStepDtos.OptionResponse;
import com.farelo.api.catalog.ProductStepDtos.StepRequest;
import com.farelo.api.catalog.ProductStepDtos.StepResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Admin configuration only; final checkout pricing is FARELO-323. */
@Service
public class ProductStepService {
    private final ProductService products;
    private final ProductStepRepository steps;
    private final ProductStepOptionRepository options;

    public ProductStepService(ProductService products, ProductStepRepository steps,
                              ProductStepOptionRepository options) {
        this.products = products;
        this.steps = steps;
        this.options = options;
    }

    @Transactional(readOnly = true)
    public List<StepResponse> list(UUID productId) {
        products.getById(productId);
        return read(productId);
    }

    /** Full replacement; validate every linked Product before deleting anything. */
    @Transactional
    public List<StepResponse> replace(UUID productId, List<StepRequest> requested) {
        Product parent = products.getById(productId);
        Set<Integer> stepOrders = new HashSet<>();
        List<List<Product>> linkedProducts = new ArrayList<>();

        for (StepRequest step : requested) {
            if (step.minChoices() > step.maxChoices()) {
                throw new ProductStepConfigurationException("minChoices cannot exceed maxChoices");
            }
            if (!stepOrders.add(step.displayOrder())) {
                throw new ProductStepConfigurationException("Repeated step displayOrder: " + step.displayOrder());
            }
            Set<Integer> optionOrders = new HashSet<>();
            Set<UUID> optionIds = new HashSet<>();
            List<Product> resolved = new ArrayList<>();
            for (OptionRequest option : step.options()) {
                if (!optionOrders.add(option.displayOrder())) {
                    throw new ProductStepConfigurationException(
                            "Repeated option displayOrder: " + option.displayOrder());
                }
                if (!optionIds.add(option.optionProductId())) {
                    throw new ProductStepConfigurationException(
                            "Repeated option product: " + option.optionProductId());
                }
                // Inactive links remain editable for a draft. Publication is FARELO-326.
                resolved.add(products.getById(option.optionProductId()));
            }
            linkedProducts.add(resolved);
        }

        for (ProductStep oldStep : steps.findByProductIdOrderByDisplayOrderAsc(productId)) {
            options.deleteAll(options.findByStepIdOrderByDisplayOrderAsc(oldStep.getId()));
            steps.delete(oldStep);
        }
        options.flush();
        steps.flush();

        for (int i = 0; i < requested.size(); i++) {
            StepRequest input = requested.get(i);
            ProductStep saved = steps.save(new ProductStep(parent, input.label(), input.minChoices(),
                    input.maxChoices(), input.displayOrder(), input.level(), input.chargeType()));
            for (int j = 0; j < input.options().size(); j++) {
                OptionRequest option = input.options().get(j);
                options.save(new ProductStepOption(saved, linkedProducts.get(i).get(j),
                        option.displayOrder(), option.price()));
            }
        }
        options.flush();
        return read(productId);
    }

    private List<StepResponse> read(UUID productId) {
        return steps.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(step -> new StepResponse(step.getId(), step.getLabel(), step.getMinChoices(),
                        step.getMaxChoices(), step.getDisplayOrder(), step.getLevel(), step.getChargeType(),
                        options.findByStepIdOrderByDisplayOrderAsc(step.getId()).stream()
                                .map(link -> new OptionResponse(link.getId(), link.getOptionProduct().getId(),
                                        link.getOptionProduct().getName(), link.getOptionProduct().isActive(),
                                        link.getDisplayOrder(), link.getPrice()))
                                .toList()))
                .toList();
    }
}
