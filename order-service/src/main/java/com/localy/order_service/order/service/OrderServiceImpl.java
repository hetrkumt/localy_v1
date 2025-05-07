package com.localy.order_service.order.service;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.domain.OrderLineItem;
import com.localy.order_service.order.dto.CartItemDto;
import com.localy.order_service.order.dto.CreateOrderRequest;
import com.localy.order_service.order.message.OrderMessage;
import com.localy.order_service.order.message.dto.OrderCreatedEvent; // OrderCreatedEvent DTO 임포트

import com.localy.order_service.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// === Reactor 및 Spring Messaging 관련 임포트 추가 ===
import reactor.core.publisher.Sinks; // Sinks 사용을 위한 임포트
import org.springframework.messaging.Message; // Spring Messaging Message 사용을 위한 임포트
import org.springframework.messaging.support.MessageBuilder; // MessageBuilder 사용을 위한 임포트
// import org.springframework.beans.factory.annotation.Qualifier; // 여러 Sinks Bean이 있을 경우 구분을 위해 필요할 수 있음
// import org.springframework.kafka.support.KafkaHeaders; // KafkaHeaders 사용 시 임포트 (선택 사항)
// ==============================================


@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMessage orderMessage;

    @Override
    @Transactional
    public Order placeOrder(CreateOrderRequest createOrderRequest) {
        List<CartItemDto> cartItems = createOrderRequest.getCartItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalStateException("주문할 상품이 없습니다.");
        }

        // 1. Order 엔티티 생성 (기본 필드들 설정)
        // builder() 시점에 totalAmount는 일단 null 또는 기본값(BigDecimal.ZERO)으로 생성됩니다.
        Order order = Order.builder()
                .userId(createOrderRequest.getUserId())
                .storeId(createOrderRequest.getStoreId())
                .orderDate(LocalDateTime.now())
                .orderStatus("PENDING") // 초기 주문 상태
                .createdAt(LocalDateTime.now())
                // totalAmount는 주문 항목(OrderLineItem)이 다 만들어진 후에 계산하여 설정할 것입니다.
                // .totalAmount(...) // 여기서 설정하지 않습니다.
                // orderLineItems 리스트는 Order 엔티티에서 초기화되거나 여기서 new ArrayList<>(); 할 수 있습니다.
                // Order 엔티티에 @Builder.Default 설정이 있다면 new ArrayList<>(); 초기화는 엔티티 빌드 시 수행됩니다.
                .orderLineItems(new ArrayList<>()) // Order 엔티티에 @Builder.Default List<OrderLineItem> = new ArrayList<>(); 없다면 명시적으로 초기화
                .build();

        // 2. CreateOrderRequest의 cartItems (List<CartItemDto>)를 OrderLineItem 엔티티 리스트로 변환
        // 각 OrderLineItem 생성 시 연관된 Order 엔티티를 설정합니다.
        List<OrderLineItem> orderLineItems = cartItems.stream()
                .map(itemDto -> {
                    OrderLineItem lineItem = OrderLineItem.builder()
                            .menuId(itemDto.getMenuId())
                            .menuName(itemDto.getMenuName())
                            .quantity(itemDto.getQuantity())
                            .unitPrice(itemDto.getUnitPrice())
                            // OrderLineItem 내부의 총 가격 계산 및 설정
                            .totalPrice(itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity())))
                            .createdAt(LocalDateTime.now())
                            // .order(...) // 여기서 빌더로 설정하는 대신, 아래에서 연관 관계를 맺어줍니다.
                            .build();
                    // === 중요: OrderLineItem과 Order 간의 연관 관계 설정 (ManyToOne 쪽) ===
                    lineItem.setOrder(order); // <-- 생성된 Order 객체를 OrderLineItem에 설정

                    return lineItem;
                })
                .collect(Collectors.toList());

        order.setOrderLineItems(orderLineItems);

        // 3. Order 엔티티에 연결된 OrderLineItem 리스트를 기반으로 주문 총액 계산
        // order.calculateTotalAmount() 메서드는 Order 객체에 orderLineItems 리스트가 채워져 있어야 정상 동작합니다.
        BigDecimal totalAmount = order.calculateTotalAmount();

        // 4. 계산된 총액을 Order 엔티티의 totalAmount 필드에 설정 (DB 저장 전에 필수!)
        order.setTotalAmount(totalAmount); // <--- **이 라인이 누락되었던 부분입니다.**

        // === 진단 목적 로그 추가 시작 ===
        System.out.println("\n--- OrderServiceImpl 진단: DB 저장 전 ---");
        System.out.println("Order ID (저장 전): " + order.getOrderId()); // 저장 전이라 ID는 null일 수 있습니다.
        System.out.println("Order 객체의 Total Amount 설정 확인: " + order.getTotalAmount()); // 총액이 올바르게 계산되었는지 확인

        if (order.getOrderLineItems() != null) {
            System.out.println("Order 객체의 OrderLineItems 리스트 상태: null 아님");
            System.out.println("Order 객체의 OrderLineItems 리스트 크기: " + order.getOrderLineItems().size()); // <-- 이 크기를 확인!

            // 리스트에 실제로 아이템이 있는지 확인 (크기가 0보다 큰 경우)
            if (!order.getOrderLineItems().isEmpty()) {
                System.out.println("Order 객체의 OrderLineItems 리스트 내용 (첫 몇 개):");
                // 첫 몇 개 아이템 정보 출력 (너무 많으면 로그 길어짐)
                order.getOrderLineItems().stream().limit(5).forEach(item -> {
                    System.out.println("  - Item: menuId=" + item.getMenuId() + ", quantity=" + item.getQuantity() + ", totalPrice=" + item.getTotalPrice());
                    // 필요하다면 부모 Order 객체 연결이 잘 되었는지도 확인 (무한루프 주의하여 단순 ID만 출력 등)
                    // System.out.println("    -> Parent Order ID: " + (item.getOrder() != null ? item.getOrder().getOrderId() : "null")); // getOrderId() 호출 시 지연로딩 프록시 문제 발생 가능성 있음
                });
                if (order.getOrderLineItems().size() > 5) {
                    System.out.println("  ... 외 " + (order.getOrderLineItems().size() - 5) + " 개 아이템 더 있음.");
                }
            } else {
                System.out.println("Order 객체의 OrderLineItems 리스트는 비어 있습니다. (리스트 객체는 생성됐지만 아이템이 추가 안됨)"); // 리스트는 생성됐지만 비어있는 경우
            }
        } else {
            System.out.println("Order 객체의 OrderLineItems 리스트가 null 입니다! (이 메시지가 보이면 심각한 문제)"); // 리스트 자체가 null인 경우
        }
        System.out.println("--- 진단 종료 ---");

        // 5. 데이터베이스에 Order 엔티티 저장
        // Order 엔티티의 @OneToMany 매핑에 cascade = CascadeType.ALL 설정이 되어 있다면,
        // Order를 저장할 때 연결된 OrderLineItem들도 함께 저장됩니다.
        // 따라서 orderLineItemRepository.saveAll(orderLineItems); 호출은 불필요해집니다.
        Order savedOrder = orderRepository.save(order);

        System.out.println("OrderServiceImpl: publishOrderCreatedEvent 호출 시도 - Order ID: " + savedOrder.getOrderId());
        orderMessage.publishOrderCreatedEvent(savedOrder);
        System.out.println("OrderServiceImpl: publishOrderCreatedEvent 호출 완료");
        return savedOrder;
    }
}