// 파일 위치: com.localy.order_service.order.controller.OrderController.java
package com.localy.order_service.order.controler;

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.dto.CreateOrderRequest;
import com.localy.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Mono<ResponseEntity<Order>> placeOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateOrderRequest createOrderRequest) {
        System.out.println("--- OrderController: POST /api/orders 요청 수신 (UserID from Header: " + userId + ") ---");
        System.out.println("--- OrderController: 수신된 CreateOrderRequest: " + createOrderRequest.toString() + " ---");

        System.out.println("--- OrderController: orderService.placeOrder 호출 시도 ---");
        Mono<Order> orderMono = orderService.placeOrder(createOrderRequest, userId)
                .doOnSubscribe(subscription -> System.out.println("--- OrderController: placeOrder Mono 구독 시작됨 ---"))
                .doOnCancel(() -> System.out.println("--- OrderController: placeOrder Mono 구독 취소됨 ---"));
        System.out.println("--- OrderController: orderService.placeOrder 호출 후 Mono 객체 받음 ---");

        return orderMono
                .map(order -> {
                    System.out.println("--- OrderController: placeOrder Mono.map 실행 (주문 객체 받음): " + order.getOrderId() + " ---");
                    return new ResponseEntity<>(order, HttpStatus.CREATED);
                })
                .doOnError(e -> System.err.println("--- OrderController: placeOrder Mono에서 에러 발생: " + e.getClass().getName() + " - " + e.getMessage() + " ---"))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    System.err.println("--- OrderController: 주문 생성 오류 (잘못된 요청) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.badRequest().build());
                })
                .onErrorResume(SecurityException.class, e -> {
                    System.err.println("--- OrderController: 주문 생성 오류 (보안) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                })
                .onErrorResume(e -> {
                    System.err.println("--- OrderController: 주문 생성 중 예상치 못한 내부 오류 - " + e.getClass().getName() + " - " + e.getMessage() + " ---");
                    // e.printStackTrace(); // 로컬 테스트 시 스택 트레이스 확인에 유용
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    System.err.println("--- OrderController: placeOrder Mono가 비어있음 (결과 없음) ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                }));
    }

    // ... (getUserOrders, getOrderDetails 메서드는 이전과 동일)
    @GetMapping("")
    public Flux<Order> getUserOrders(@RequestHeader("X-User-Id") String userId) {
        System.out.println("--- OrderController: GET /api/orders 요청 수신 (UserID from Header: " + userId + ") ---");
        return orderService.findOrdersByUserId(userId);
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<Order>> getOrderDetails(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long orderId) {
        System.out.println("--- OrderController: GET /api/orders/" + orderId + " 요청 수신 (UserID from Header: " + userId + ") ---");
        return orderService.findOrderDetails(orderId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e ->
                        Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
