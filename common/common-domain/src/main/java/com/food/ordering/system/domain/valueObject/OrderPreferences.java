package com.food.ordering.system.domain.valueObject;

import lombok.Builder;

import java.util.List;

@Builder
public record OrderPreferences(
        List<String> removeIngredientes,
        List<String> addIngredients,
        SpiceLevel spiceLevel,
        String specialInstructions,
        String deliveryInstructions
) {
}
