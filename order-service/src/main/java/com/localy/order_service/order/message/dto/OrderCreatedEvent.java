package com.localy.order_service.order.message.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private Long orderId;
    private String userId;
    private String storeId;
    private BigDecimal totalAmount;
}