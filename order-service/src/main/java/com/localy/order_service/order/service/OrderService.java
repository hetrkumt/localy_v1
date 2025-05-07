package com.localy.order_service.order.service;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.dto.CreateOrderRequest;

public interface OrderService {
    Order placeOrder(CreateOrderRequest createOrderRequest);
}