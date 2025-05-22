package com.localy.order_service.order.repository;

import com.localy.order_service.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository; // JpaRepository 임포트
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> { // JpaRepository<Order, Long>로 변경

    // 특정 사용자의 주문 목록을 주문 날짜 최신순으로 조회
    List<Order> findByUserIdOrderByOrderDateDesc(String userId); // 반환 타입을 List<Order>로 변경

    // 특정 사용자의 특정 주문 조회
    Optional<Order> findByOrderIdAndUserId(Long orderId, String userId); // 반환 타입을 Optional<Order>로 변경
}