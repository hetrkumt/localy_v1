package com.localy.payment_service.order.consumer;

import com.localy.payment_service.order.consumer.dto.OrderCreatedEvent;
import com.localy.payment_service.payment.service.PaymentProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class OrderCreatedEventConsumerConfig {

    private final PaymentProcessorService paymentProcessorService;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreatedConsumer() {
        System.out.println("PaymentService: orderCreatedConsumer Consumer Bean 활성화됨"); // <-- Bean 활성화 로그 추가 (시작 시)
        return event -> {
            // === 메시지 수신 로그 (역직렬화 성공 시 이 블록 실행됨) ===
            System.out.println("PaymentService: OrderCreatedEvent 메시지 수신! Order ID: " + event.getOrderId() + ", Total Amount: " + event.getTotalAmount()); // <-- 수신 로그 추가
            // ======================
            // 이제 이 아래에 paymentProcessorService 호출 로직이 있습니다.
            System.out.println("PaymentService: PaymentProcessorService::processOrderCreatedEvent 호출 시도"); // <-- 처리 메서드 호출 전 로그
            paymentProcessorService.processOrderCreatedEvent(event); // 메시지 처리 로직 호출
            System.out.println("PaymentService: PaymentProcessorService::processOrderCreatedEvent 호출 완료"); // <-- 처리 메서드 호출 후 로그
        };
    }
}