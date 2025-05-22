package com.localy.order_service.order.repository;

import com.localy.order_service.order.domain.OrderLineItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderLineItemRepository extends ReactiveCrudRepository<OrderLineItem, Long> {
    Flux<OrderLineItem> findByOrderIdFk(Long orderIdFk);

    // Flux<OrderLineItem> findByMenuId(String menuId);
}
