package com.food.ordering.system.payment.service.domain.valueObject;

import com.food.ordering.system.domain.valueObject.BaseId;

import java.util.UUID;

public class PaymentId extends BaseId<UUID> {
    public PaymentId(UUID value) {
        super(value);
    }
}
