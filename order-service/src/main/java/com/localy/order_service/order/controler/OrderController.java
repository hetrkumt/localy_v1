package com.localy.order_service.order.controler;

import com.localy.order_service.order.service.OrderService;
import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;


    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestBody CreateOrderRequest createOrderRequest) {

        System.out.println("주문 요청 받음: " + createOrderRequest);

        Order order = orderService.placeOrder(createOrderRequest);

        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }
}