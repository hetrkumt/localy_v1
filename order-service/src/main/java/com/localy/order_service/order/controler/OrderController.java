// 파일 위치: com.localy.order_service.order.controller.OrderController.java
package com.localy.order_service.order.controler; // 패키지 이름 확인 필요

import com.localy.order_service.order.domain.Order;
import com.localy.order_service.order.dto.CreateOrderRequest; // 기존 DTO
import com.localy.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // GetMapping, PathVariable, RequestHeader 추가
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.NoSuchElementException; // 예외 클래스 임포트

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 기존 주문 생성 엔드포인트
    @PostMapping
    public Mono<ResponseEntity<Order>> placeOrder(@RequestBody CreateOrderRequest createOrderRequest) {
        // 참고: createOrderRequest에 userId 필드가 있다면,
        // 실제로는 @RequestHeader("X-User-Id") String userId 로 받아서 서비스에 전달하고,
        // CreateOrderRequest에서는 userId 필드를 제거하는 것이 더 안전합니다.
        // 여기서는 기존 구조를 유지한다고 가정합니다.
        System.out.println("--- OrderController: POST /api/orders 요청 수신 (UserID from DTO: " + createOrderRequest.getUserId() + ") ---");
        return orderService.placeOrder(createOrderRequest)
                .map(order -> new ResponseEntity<>(order, HttpStatus.CREATED))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().body(null))) // 예시 오류 처리
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)));
    }

    // --- 주문 내역 조회 엔드포인트 추가 ---

    /**
     * 현재 로그인한 사용자의 모든 주문 목록을 조회합니다.
     * @param userId 엣지 서비스에서 전달받은 X-User-Id 헤더 값
     * @return 주문 목록 (Order 엔티티 리스트)
     */
    @GetMapping("") // GET /api/orders
    public Flux<Order> getUserOrders(@RequestHeader("X-User-Id") String userId) {
        System.out.println("--- OrderController: GET /api/orders 요청 수신 (UserID from Header: " + userId + ") ---");
        return orderService.findOrdersByUserId(userId);
        // 에러 처리는 서비스단에서 Mono.error()로 던지면 WebFlux의 기본 예외 처리 또는
        // @ControllerAdvice를 통해 처리될 수 있습니다.
        // 여기서는 간단하게 서비스의 Flux를 그대로 반환합니다.
    }

    /**
     * 현재 로그인한 사용자의 특정 주문 상세 정보를 조회합니다.
     * @param userId  엣지 서비스에서 전달받은 X-User-Id 헤더 값
     * @param orderId 조회할 주문의 ID (경로 변수)
     * @return 특정 주문 정보 (Order 엔티티) 또는 404 Not Found
     */
    @GetMapping("/{orderId}") // GET /api/orders/{orderId}
    public Mono<ResponseEntity<Order>> getOrderDetails(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long orderId) {
        System.out.println("--- OrderController: GET /api/orders/" + orderId + " 요청 수신 (UserID from Header: " + userId + ") ---");
        return orderService.findOrderDetails(orderId, userId)
                .map(ResponseEntity::ok) // 조회 성공 시 200 OK 와 함께 Order 객체 반환
                .onErrorResume(NoSuchElementException.class, e -> // 서비스에서 주문을 못 찾거나 권한 없는 경우
                        Mono.just(ResponseEntity.notFound().build())) // 404 Not Found
                .onErrorResume(IllegalArgumentException.class, e -> // 서비스에서 userId 또는 orderId가 유효하지 않은 경우
                        Mono.just(ResponseEntity.badRequest().build())) // 400 Bad Request
                .defaultIfEmpty(ResponseEntity.notFound().build()); // 혹시 Mono가 비어서 올 경우 (이론상 switchIfEmpty로 처리됨)
    }
}
