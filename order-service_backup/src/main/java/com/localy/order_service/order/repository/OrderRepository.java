package com.localy.order_service.order.repository;

import com.localy.order_service.order.domain.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    Flux<Order> findByUserIdOrderByOrderDateDesc(String userId);
    Mono<Order> findByOrderIdAndUserId(Long orderId, String userId);
}