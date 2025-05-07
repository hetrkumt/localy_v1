package com.localy.cart_service.orderIntegration.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private String userId;
    private String storeId; // 주문 대상 가게 ID
    private List<CartItemDto> cartItems;
}