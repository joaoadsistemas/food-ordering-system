package com.food.ordering.system.restaurant.service.domain.valueObject;

import com.food.ordering.system.domain.valueObject.BaseId;

import java.util.UUID;

public class OrderApprovalId extends BaseId<UUID> {
    public OrderApprovalId(UUID value) {
        super(value);
    }
}
