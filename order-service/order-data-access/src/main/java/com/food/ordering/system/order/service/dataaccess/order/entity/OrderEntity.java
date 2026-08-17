package com.food.ordering.system.order.service.dataaccess.order.entity;

import com.food.ordering.system.domain.valueObject.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    private UUID id;
    private UUID customerId;
    private UUID restaurantId;
    private UUID trackingId;
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
    private String failureMessages;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private OrderAddressEntity address;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItemEntity> items;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OrderEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
