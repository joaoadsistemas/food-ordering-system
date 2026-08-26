 package com.food.ordering.system.payment.service.domain.valueObject;

import com.food.ordering.system.domain.valueObject.BaseId;

import java.util.UUID;

 public class CreditHistoryId extends BaseId<UUID> {
     public CreditHistoryId(UUID value) {
         super(value);
     }
 }
