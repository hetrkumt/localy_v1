package com.localy.order_service.order.repository;

import com.localy.order_service.order.domain.OrderLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, Long> {
    // 필요한 추가적인 쿼리 메소드를 정의할 수 있습니다.
}