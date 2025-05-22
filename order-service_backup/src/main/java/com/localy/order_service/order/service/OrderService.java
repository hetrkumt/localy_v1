// 파일 위치: com.localy.order_service.order.service.OrderService.java
package com.localy.order_service.order.service;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.domain.OrderLineItem;
import com.localy.order_service.order.dto.CreateOrderRequest;
import com.localy.order_service.order.dto.CartItemDto;
import com.localy.order_service.order.repository.OrderLineItemRepository;
import com.localy.order_service.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;

    @Transactional
    public Mono<Order> placeOrder(CreateOrderRequest createOrderRequest, String userId) {
        System.out.println(String.format("--- OrderService: placeOrder 시작 - UserID: %s, StoreID: %s, CartItem 개수: %d ---",
                userId, createOrderRequest.getStoreId(), createOrderRequest.getCartItems() != null ? createOrderRequest.getCartItems().size() : 0));

        // 1. 입력 유효성 검증
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: placeOrder 오류 - 사용자 ID 누락 ---");
            return Mono.error(new SecurityException("사용자 ID가 제공되지 않았습니다."));
        }
        if (createOrderRequest.getStoreId() == null) {
            System.err.println("--- OrderService: placeOrder 오류 - 가게 ID 누락 ---");
            return Mono.error(new IllegalArgumentException("가게 ID는 필수입니다."));
        }
        if (createOrderRequest.getCartItems() == null || createOrderRequest.getCartItems().isEmpty()) {
            System.err.println("--- OrderService: placeOrder 오류 - 주문 상품 없음 ---");
            return Mono.error(new IllegalArgumentException("주문할 상품이 없습니다."));
        }
        System.out.println("--- OrderService: placeOrder - 입력 유효성 검증 통과 ---");

        Order initialOrder = Order.builder()
                .userId(userId)
                .storeId(createOrderRequest.getStoreId())
                .orderDate(LocalDateTime.now())
                .orderStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        System.out.println("--- OrderService: placeOrder - 초기 Order 객체 생성 완료: " + initialOrder.toString());

        // 3. Order를 먼저 DB에 저장하여 orderId 할당받기
        return orderRepository.save(initialOrder)
                .doOnSuccess(savedInitialOrder -> System.out.println(String.format("--- OrderService: placeOrder - 초기 Order 저장 성공 (ID: %d) ---", savedInitialOrder.getOrderId())))
                .doOnError(e -> System.err.println(String.format("--- OrderService: placeOrder - 초기 Order 저장 실패: %s ---", e.getMessage())))
                .flatMap(savedOrder -> { // 저장된 Order (orderId 포함)
                    System.out.println(String.format("--- OrderService: placeOrder - OrderLineItem 변환 및 저장 시작 (Order ID: %d) ---", savedOrder.getOrderId()));

                    // 4. CartItemDto를 OrderLineItem으로 변환
                    List<OrderLineItem> lineItemsToCreate = createOrderRequest.getCartItems().stream()
                            .map(cartItemDto -> {
                                BigDecimal itemTotalPrice = cartItemDto.getUnitPrice().multiply(BigDecimal.valueOf(cartItemDto.getQuantity()));
                                return OrderLineItem.builder()
                                        .orderIdFk(savedOrder.getOrderId()) // 할당된 Order ID 사용
                                        .menuId(cartItemDto.getMenuId())
                                        .menuName(cartItemDto.getMenuName())
                                        .quantity(cartItemDto.getQuantity())
                                        .unitPrice(cartItemDto.getUnitPrice())
                                        .totalPrice(itemTotalPrice)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                            }).collect(Collectors.toList());
                    System.out.println(String.format("--- OrderService: placeOrder - %d개의 OrderLineItem 객체 생성 완료 ---", lineItemsToCreate.size()));

                    // 5. 생성된 OrderLineItem들을 DB에 저장 (saveAll 사용)
                    return orderLineItemRepository.saveAll(lineItemsToCreate)
                            .collectList() // Flux<OrderLineItem> -> Mono<List<OrderLineItem>>
                            .doOnSuccess(savedItems -> System.out.println(String.format("--- OrderService: placeOrder - %d개의 OrderLineItem DB 저장 성공 ---", savedItems.size())))
                            .doOnError(e -> System.err.println(String.format("--- OrderService: placeOrder - OrderLineItem DB 저장 실패: %s ---", e.getMessage())))
                            .flatMap(savedLineItems -> {
                                // 6. 저장된 OrderLineItem들로 Order 객체의 @Transient 필드 채우고, 총액 계산
                                savedOrder.setOrderLineItems(new ArrayList<>(savedLineItems)); // DB에 저장된 실제 객체들로 설정
                                savedOrder.setTotalAmount(savedOrder.calculateTotalAmount());
                                System.out.println(String.format("--- OrderService: placeOrder - OrderLineItems 설정 및 총액 계산 완료 (총액: %s) ---", savedOrder.getTotalAmount()));

                                // 7. 총액 등이 업데이트된 Order를 다시 DB에 저장
                                System.out.println(String.format("--- OrderService: placeOrder - 최종 Order 업데이트 저장 시도 (ID: %d) ---", savedOrder.getOrderId()));
                                return orderRepository.save(savedOrder)
                                        .doOnSuccess(finalOrder -> System.out.println(String.format("--- OrderService: placeOrder - 최종 Order 업데이트 저장 성공 (ID: %d) ---", finalOrder.getOrderId())))
                                        .doOnError(e -> System.err.println(String.format("--- OrderService: placeOrder - 최종 Order 업데이트 저장 실패: %s ---", e.getMessage())));
                            });
                })
                .doOnSuccess(finalOrder -> {
                    System.out.println(String.format("--- OrderService: 주문 생성 및 모든 항목 처리 완료 (최종 OrderID: %d) ---", finalOrder.getOrderId()));
                    // TODO: 주문 생성 이벤트 발행 (Kafka 등) - 트랜잭션 커밋 후 발행 권장 (@TransactionalEventListener)
                })
                .doOnError(e -> System.err.println(String.format("--- OrderService: 주문 생성 중 전체 파이프라인에서 오류 발생 - %s ---", e.getMessage())));
    }

    // ... (findOrdersByUserId 및 findOrderDetails 메서드는 이전과 동일하게 유지하되, System.out/err 사용)
    public Flux<Order> findOrdersByUserId(String userId) {
        System.out.println(String.format("--- OrderService: findOrdersByUserId 호출 (UserID: %s) ---", userId));
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: findOrdersByUserId - 사용자 ID 누락 ---");
            return Flux.error(new IllegalArgumentException("사용자 ID는 주문 내역 조회에 필수입니다."));
        }
        return orderRepository.findByUserIdOrderByOrderDateDesc(userId)
                .flatMap(order -> {
                    System.out.println(String.format("--- OrderService: findOrdersByUserId - Order %d에 대한 OrderLineItems 로드 시도 ---", order.getOrderId()));
                    return orderLineItemRepository.findByOrderIdFk(order.getOrderId())
                            .collectList()
                            .map(items -> {
                                order.setOrderLineItems(items);
                                System.out.println(String.format("--- OrderService: findOrdersByUserId - Order %d에 %d개 OrderLineItems 설정 완료 ---", order.getOrderId(), items.size()));
                                return order;
                            });
                })
                .doOnComplete(() -> System.out.println(String.format("--- OrderService: 사용자 주문 목록 조회 완료 (UserID: %s) ---", userId)))
                .doOnError(e -> System.err.println(String.format("--- OrderService: 사용자 주문 목록 조회 중 오류 발생 - %s ---", e.getMessage())));
    }

    public Mono<Order> findOrderDetails(Long orderId, String userId) {
        System.out.println(String.format("--- OrderService: findOrderDetails 호출 (OrderID: %d, UserID: %s) ---", orderId, userId));
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- OrderService: findOrderDetails - 사용자 ID 누락 ---");
            return Mono.error(new IllegalArgumentException("사용자 ID는 특정 주문 조회에 필수입니다."));
        }
        if (orderId == null) {
            System.err.println("--- OrderService: findOrderDetails - 주문 ID 누락 ---");
            return Mono.error(new IllegalArgumentException("주문 ID는 특정 주문 조회에 필수입니다."));
        }
        return orderRepository.findByOrderIdAndUserId(orderId, userId)
                .switchIfEmpty(Mono.defer(() -> {
                    System.err.println(String.format("--- OrderService: findOrderDetails - 주문 없음 (OrderID: %d, UserID: %s) ---", orderId, userId));
                    return Mono.error(new NoSuchElementException("주문 ID " + orderId + "에 해당하는 주문을 찾을 수 없거나 해당 사용자의 주문이 아닙니다."));
                }))
                .flatMap(order -> {
                    System.out.println(String.format("--- OrderService: findOrderDetails - Order %d에 대한 OrderLineItems 로드 시도 ---", order.getOrderId()));
                    return orderLineItemRepository.findByOrderIdFk(order.getOrderId())
                            .collectList()
                            .map(items -> {
                                order.setOrderLineItems(items);
                                System.out.println(String.format("--- OrderService: findOrderDetails - Order %d에 %d개 OrderLineItems 설정 완료 ---", order.getOrderId(), items.size()));
                                return order;
                            });
                })
                .doOnSuccess(order -> {
                    if (order != null) {
                        System.out.println(String.format("--- OrderService: 특정 주문 상세 조회 완료 (OrderID: %d) ---", order.getOrderId()));
                    }
                })
                .doOnError(e -> System.err.println(String.format("--- OrderService: 특정 주문 상세 조회 중 오류 발생 - %s ---", e.getMessage())));
    }
}
