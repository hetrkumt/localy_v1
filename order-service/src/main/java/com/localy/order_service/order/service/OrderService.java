// 파일 위치: com.localy.order_service.order.service.OrderService.java
package com.localy.order_service.order.service;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.domain.OrderLineItem;
import com.localy.order_service.order.dto.CartItemDto;
import com.localy.order_service.order.dto.CreateOrderRequest;
import com.localy.order_service.order.message.OrderMessage; // Kafka 메시지 관련 클래스 (필요시 사용)
import com.localy.order_service.order.repository.OrderRepository;
// OrderLineItemRepository는 Order의 CascadeType.ALL로 인해 직접적인 save 호출이 필요 없을 수 있음
// import com.localy.order_service.order.repository.OrderLineItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException; // 예외 임포트
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional // 클래스 레벨에 Transactional 선언 (모든 public 메서드에 적용)
public class OrderService {

    private final OrderRepository orderRepository;
    // private final OrderLineItemRepository orderLineItemRepository; // Order에 Cascade 설정 시 불필요할 수 있음
    private final OrderMessage orderMessage; // Kafka 메시지 발행용

    public Order placeOrder(CreateOrderRequest createOrderRequest, String userId) { // userId 파라미터 추가
        System.out.println(String.format("--- OrderService: placeOrder 시작 - UserID from Header: %s, StoreID: %s ---", userId, createOrderRequest.getStoreId()));

        List<CartItemDto> cartItems = createOrderRequest.getCartItems();
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: placeOrder 오류 - 사용자 ID 누락 ---");
            throw new SecurityException("사용자 ID가 제공되지 않았습니다.");
        }
        if (createOrderRequest.getStoreId() == null) {
            System.err.println("--- OrderService: placeOrder 오류 - 가게 ID 누락 ---");
            throw new IllegalArgumentException("가게 ID는 필수입니다.");
        }
        if (cartItems == null || cartItems.isEmpty()) {
            System.err.println("--- OrderService: placeOrder 오류 - 주문 상품 없음 ---");
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }
        System.out.println("--- OrderService: placeOrder - 입력 유효성 검증 통과 ---");

        Order order = Order.builder()
                .userId(userId) // 헤더에서 받은 userId 사용
                .storeId(createOrderRequest.getStoreId()) // DTO의 storeId 타입이 String이라고 가정
                .orderDate(LocalDateTime.now())
                .orderStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .orderLineItems(new ArrayList<>())
                .build();
        System.out.println("--- OrderService: placeOrder - 초기 Order 객체 생성 완료 ---");

        List<OrderLineItem> orderLineItems = cartItems.stream()
                .map(itemDto -> {
                    OrderLineItem lineItem = OrderLineItem.builder()
                            .menuId(itemDto.getMenuId())
                            .menuName(itemDto.getMenuName())
                            .quantity(itemDto.getQuantity())
                            .unitPrice(itemDto.getUnitPrice())
                            .totalPrice(itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity())))
                            .createdAt(LocalDateTime.now())
                            .order(order) // 양방향 연관관계 설정
                            .build();
                    return lineItem;
                })
                .collect(Collectors.toList());

        order.setOrderLineItems(orderLineItems);
        System.out.println(String.format("--- OrderService: placeOrder - OrderLineItems 변환 및 Order에 설정 완료 (%d개 항목) ---", orderLineItems.size()));

        order.setTotalAmount(order.calculateTotalAmount());
        System.out.println(String.format("--- OrderService: placeOrder - 주문 총액 계산 완료: %s ---", order.getTotalAmount()));

        // JPA의 CascadeType.ALL 설정으로 Order 저장 시 OrderLineItem도 함께 저장됨
        System.out.println("--- OrderService: placeOrder - orderRepository.save 호출 전 ---");
        Order savedOrder = orderRepository.save(order);
        System.out.println(String.format("--- OrderService: 주문 생성 및 항목 저장 완료 (OrderID: %d) ---", savedOrder.getOrderId()));

        // Kafka 이벤트 발행
        System.out.println("OrderService: publishOrderCreatedEvent 호출 시도 - Order ID: " + savedOrder.getOrderId());
        orderMessage.publishOrderCreatedEvent(savedOrder); // Kafka 메시지 발행
        System.out.println("OrderService: publishOrderCreatedEvent 호출 완료");

        return savedOrder;
    }

    // 특정 사용자의 모든 주문 목록 조회 (JPA용)
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<Order> findOrdersByUserId(String userId) {
        System.out.println(String.format("--- OrderService: findOrdersByUserId 호출 (UserID: %s) ---", userId));
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: findOrdersByUserId - 사용자 ID 누락 ---");
            throw new IllegalArgumentException("사용자 ID는 주문 내역 조회에 필수입니다.");
        }
        List<Order> orders = orderRepository.findByUserIdOrderByOrderDateDesc(userId);
        // JPA EAGER 로딩 또는 Fetch Join을 사용하지 않았다면, orderLineItems는 여기서 프록시 객체일 수 있음.
        // DTO로 변환하여 반환하거나, 실제 사용 시점에 로드되도록 할 수 있음.
        // 여기서는 Order 엔티티를 그대로 반환하며, 직렬화 시점에 orderLineItems가 로드된다고 가정.
        System.out.println(String.format("--- OrderService: 사용자 주문 목록 조회 완료 (UserID: %s, %d 건) ---", userId, orders.size()));
        return orders;
    }

    // 특정 사용자의 특정 주문 상세 정보 조회 (JPA용)
    @Transactional(readOnly = true)
    public Order findOrderDetails(Long orderId, String userId) {
        System.out.println(String.format("--- OrderService: findOrderDetails 호출 (OrderID: %d, UserID: %s) ---", orderId, userId));
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: findOrderDetails - 사용자 ID 누락 ---");
            throw new IllegalArgumentException("사용자 ID는 특정 주문 조회에 필수입니다.");
        }
        if (orderId == null) {
            System.err.println("--- OrderService: findOrderDetails - 주문 ID 누락 ---");
            throw new IllegalArgumentException("주문 ID는 특정 주문 조회에 필수입니다.");
        }
        Order order = orderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> {
                    System.err.println(String.format("--- OrderService: findOrderDetails - 주문 없음 (OrderID: %d, UserID: %s) ---", orderId, userId));
                    return new NoSuchElementException("주문 ID " + orderId + "에 해당하는 주문을 찾을 수 없거나 해당 사용자의 주문이 아닙니다.");
                });
        // JPA EAGER 로딩 또는 @EntityGraph 등으로 orderLineItems를 함께 로드하거나,
        // 여기서 명시적으로 초기화 (호출)하여 로드할 수 있습니다.
        // 예: order.getOrderLineItems().size(); // LAZY 로딩된 컬렉션 강제 초기화
        System.out.println(String.format("--- OrderService: 특정 주문 상세 조회 완료 (OrderID: %d, 항목 수: %d) ---", order.getOrderId(), order.getOrderLineItems() != null ? order.getOrderLineItems().size() : 0));
        return order;
    }
}
