package com.localy.payment_service.order.consumer.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderCreatedEvent {
    private Long orderId;
    private String userId;
    private String storeId;
    private BigDecimal totalAmount;

    public OrderCreatedEvent() {
    }
}