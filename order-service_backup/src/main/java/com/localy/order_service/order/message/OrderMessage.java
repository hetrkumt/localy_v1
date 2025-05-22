package com.localy.order_service.order.message;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.message.dto.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class OrderMessage {

    // Service 레이어로부터 전달받은 마지막 주문 정보를 임시로 저장하는 필드
    // 이 필드는 동시성 문제가 발생할 수 있는 잠재적인 위험이 있습니다.
    private Order latestOrderToPublish;

    // OrderServiceImpl에서 호출하여 발행할 주문 정보를 이 클래스에 전달하는 메서드
    public void publishOrderCreatedEvent(Order order) {
        // === publishOrderCreatedEvent 메서드 진입 로그 ===
        System.out.println("OrderMessage: publishOrderCreatedEvent 메서드 호출됨 - Order ID: " + (order != null ? order.getOrderId() : "null")); // <-- 로그 추가
        this.latestOrderToPublish = order;
        // === latestOrderToPublish 필드 설정 후 로그 ===
        System.out.println("OrderMessage: latestOrderToPublish 필드 설정됨 - 현재 값: " + (this.latestOrderToPublish != null ? this.latestOrderToPublish.getOrderId() : "null")); // <-- 로그 추가
    }


    // Spring Cloud Stream에게 메시지를 공급하는 Supplier Bean 정의
    // Spring Cloud Stream 바인더는 이 Supplier를 주기적으로 호출(폴링)하여 메시지를 가져갑니다.
    @Bean
    public Supplier<OrderCreatedEvent> orderCreatedProducer() {
        // Supplier 함수형 인터페이스의 get() 메서드를 구현
        return () -> {
            // === get() 메서드 진입 로그 (매 폴링 시마다 호출됨) ===
            //System.out.println("OrderMessage: orderCreatedProducer().get() 메서드 호출됨 - 현재 latestOrderToPublish 상태: " + (latestOrderToPublish != null ? latestOrderToPublish.getOrderId() : "null")); // <-- 로그 추가

            // 발행할 주문 정보(latestOrderToPublish)가 있는지 확인
            if (latestOrderToPublish != null) {
                // === 메시지 발행 준비 완료 직전 로그 ===
                System.out.println("OrderMessage: get() 메서드 - latestOrderToPublish 발견됨, 메시지 발행 준비 시작"); // <-- 로그 추가

                Order orderToPublish = latestOrderToPublish;
                latestOrderToPublish = null; // 한 번 발행 후 초기화

                OrderCreatedEvent event = OrderCreatedEvent.builder()
                        .orderId(orderToPublish.getOrderId())
                        .userId(orderToPublish.getUserId())
                        .totalAmount(orderToPublish.getTotalAmount())
                        .storeId(orderToPublish.getStoreId())
                        .build();
                System.out.println("OrderMessage: Supplier가 메시지 발행 준비 완료 - Order ID: " + event.getOrderId()); // 기존 로그

                return event; // 메시지 객체 반환

            } else {
                // === 보낼 메시지가 없을 때 로그 ===
                //System.out.println("OrderMessage: get() 메서드 - latestOrderToPublish는 null, 보낼 메시지 없음"); // <-- 로그 추가
            }

            // 현재 보낼 메시지가 없으면 Spring Cloud Stream에게 null을 반환하여 알림
            return null; // null 반환
        };

    }
}