// 파일 위치: com.localy.order_service.order.service.OrderService.java
package com.localy.order_service.order.service;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.dto.CreateOrderRequest; // 기존 DTO
import com.localy.order_service.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.NoSuchElementException; // 예외 클래스 임포트

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    // private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate; // 이벤트 발행용 (기존 코드)
    // private static final String ORDER_CREATED_TOPIC = "order-created"; // 기존 코드

    // 기존 placeOrder 메서드 (예시로 유지, 실제 구현은 다를 수 있음)
    public Mono<Order> placeOrder(CreateOrderRequest createOrderRequest) {
        System.out.println("--- OrderService: placeOrder 호출 (UserID: " + createOrderRequest.getUserId() + ") ---");
        // TODO: createOrderRequest의 cartItems를 OrderLineItem으로 변환하는 로직
        // TODO: Order 객체 생성 및 totalAmount 계산
        Order newOrder = Order.builder()
                .userId(createOrderRequest.getUserId()) // 실제로는 X-User-Id 헤더 값 사용 권장
                .storeId(createOrderRequest.getStoreId())
                // .orderLineItems(...) // 변환된 OrderLineItem 리스트 설정
                .orderDate(LocalDateTime.now())
                // .totalAmount(...) // 계산된 총액 설정
                .orderStatus("PENDING") // 초기 주문 상태
                .createdAt(LocalDateTime.now())
                .build();

        // newOrder.setTotalAmount(newOrder.calculateTotalAmount()); // 총액 계산

        // OrderLineItem에 Order 객체 설정 (양방향 연관관계)
        // newOrder.getOrderLineItems().forEach(item -> item.setOrder(newOrder));


        return orderRepository.save(newOrder)
                .doOnSuccess(savedOrder -> {
                    System.out.println("--- OrderService: 주문 생성 완료 (ID: " + savedOrder.getOrderId() + ") ---");
                    // TODO: 주문 생성 이벤트 발행 (Kafka 등)
                    // OrderCreatedEvent event = OrderCreatedEvent.builder() ... build();
                    // kafkaTemplate.send(ORDER_CREATED_TOPIC, event);
                })
                .doOnError(e -> System.err.println("--- OrderService: 주문 생성 오류 - " + e.getMessage() + " ---"));
    }

    // --- 주문 내역 조회 기능 추가 ---

    /**
     * 특정 사용자의 모든 주문 목록을 주문 날짜 최신순으로 조회합니다.
     * @param userId 조회할 사용자의 ID (X-User-Id 헤더에서 전달받음)
     * @return 주문 목록을 담은 Flux
     */
    public Flux<Order> findOrdersByUserId(String userId) {
        System.out.println("--- OrderService: findOrdersByUserId 호출 (UserID: " + userId + ") ---");
        if (userId == null || userId.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("사용자 ID는 주문 내역 조회에 필수입니다."));
        }
        return orderRepository.findByUserIdOrderByOrderDateDesc(userId)
                .doOnComplete(() -> System.out.println("--- OrderService: 사용자 주문 목록 조회 완료 (UserID: " + userId + ") ---"))
                .doOnError(e -> System.err.println("--- OrderService: 사용자 주문 목록 조회 중 오류 발생 - " + e.getMessage() + " ---"));
    }

    /**
     * 특정 사용자의 특정 주문 상세 정보를 조회합니다.
     * @param orderId 조회할 주문의 ID
     * @param userId  해당 주문의 소유자임을 확인할 사용자 ID (X-User-Id 헤더에서 전달받음)
     * @return 특정 주문 정보를 담은 Mono
     */
    public Mono<Order> findOrderDetails(Long orderId, String userId) {
        System.out.println("--- OrderService: findOrderDetails 호출 (OrderID: " + orderId + ", UserID: " + userId + ") ---");
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("사용자 ID는 특정 주문 조회에 필수입니다."));
        }
        if (orderId == null) {
            return Mono.error(new IllegalArgumentException("주문 ID는 특정 주문 조회에 필수입니다."));
        }
        return orderRepository.findByOrderIdAndUserId(orderId, userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("주문 ID " + orderId + "에 해당하는 주문을 찾을 수 없거나 해당 사용자의 주문이 아닙니다.")))
                .doOnSuccess(order -> {
                    if (order != null) { // null 체크 추가 (switchIfEmpty가 항상 예외를 던지지는 않음)
                        System.out.println("--- OrderService: 특정 주문 상세 조회 완료 (OrderID: " + order.getOrderId() + ") ---");
                    }
                })
                .doOnError(e -> System.err.println("--- OrderService: 특정 주문 상세 조회 중 오류 발생 - " + e.getMessage() + " ---"));
    }

    // TODO: 주문 상태 변경 등의 다른 서비스 메서드들...
}
