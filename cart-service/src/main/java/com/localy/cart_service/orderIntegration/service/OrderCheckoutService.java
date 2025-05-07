package com.localy.cart_service.orderIntegration.service;

import com.localy.cart_service.cart.repository.CartRepository; // CartRepository import
import com.localy.cart_service.cart.domain.Cart; // Cart 도메인 import (경로 확인)

import com.localy.cart_service.orderIntegration.config.client.OrderServiceClient;
import com.localy.cart_service.orderIntegration.dto.CartItemDto;
import com.localy.cart_service.orderIntegration.dto.CheckoutResult;
import com.localy.cart_service.orderIntegration.dto.CreateOrderRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor; // Lombok import
import org.slf4j.Logger; // <-- Logger import
import org.slf4j.LoggerFactory; // <-- LoggerFactory import

import org.springframework.stereotype.Service; // Service 어노테이션 import

import java.util.List; // List import
import java.util.stream.Collectors; // Collectors import
import java.util.Map; // Cart.getCartItems()가 Map<String, CartItem> 이라면 Map import 필요

@Service // 스프링 서비스 빈으로 등록
@RequiredArgsConstructor // Lombok: final 필드를 사용하는 생성자 자동 생성 (의존성 주입에 사용)
public class OrderCheckoutService {

    // 로깅을 위한 Logger 인스턴스 생성 (slf4j 사용 권장)
    private static final Logger log = LoggerFactory.getLogger(OrderCheckoutService.class);

    private final CartRepository cartRepository; // CartRepository 주입
    // private final OrderIntegrationService orderIntegrationService; // 기존 RestTemplate 서비스는 더 이상 필요 없습니다.
    private final OrderServiceClient orderServiceClient; // <-- Feign 클라이언트 인터페이스 주입

    // 장바구니 정보를 바탕으로 주문 생성을 시도하는 메서드
    public CheckoutResult checkout(String userId) {
        log.info("Checkout 시도: 사용자 ID={}", userId); // 체크아웃 시작 로그

        // 사용자 ID로 장바구니 조회
        Cart cart = cartRepository.findById(userId).orElse(null);

        // === 로그 추가: Redis에서 로딩된 장바구니 객체 상태 확인 ===
        log.info("CartService: Redis에서 로딩된 장바구니 객체: {}", cart);
        log.info("CartService: 로딩된 장바구니 userId: {}", cart != null ? cart.getUserId() : "null");
        log.info("CartService: 로딩된 장바구니 storeId: {}", cart != null ? cart.getStoreId() : "null");
        log.info("CartService: 로딩된 장바구니 cartItems 맵 상태: {}", cart != null ? cart.getCartItems() : "null");
        log.info("CartService: 로딩된 장바구니 cartItems 맵 크기: {}",
                cart != null && cart.getCartItems() != null ? cart.getCartItems().size() : 0);
        // Cart 객체 및 CartItem 객체에 @ToString 또는 toString() 구현이 되어있어야 상세 내용 출력됩니다.
        // ===========================================================


        // 1. 장바구니 유효성 체크 (null, 비어있는지 등)
        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            String errorMessage = "장바구니가 비어 있거나 찾을 수 없습니다. 상품을 먼저 담아주세요.";
            log.warn("Checkout 실패: {}", errorMessage); // 경고 로그
            return CheckoutResult.failure(errorMessage);
        }

        // 장바구니에서 가게 ID와 장바구니 ID 를 가져옵니다.
        String storeId = cart.getStoreId();
        // 2. 장바구니에 가게 ID가 설정되어 있는지 체크
        if (storeId == null) {
            String errorMessage = "장바구니에 가게 정보가 없습니다. (비정상 상태)";
            log.error("Checkout 실패: {}", errorMessage); // 에러 로그
            return CheckoutResult.failure(errorMessage);
        }

        // 3. 장바구니 아이템 목록을 주문 서비스 호출에 필요한 DTO 리스트로 변환
        // CartItem 객체가 Map의 value로 저장되어 있다고 가정합니다.
        List<CartItemDto> orderItems = cart.getCartItems().values().stream()
                .map(cartItem -> new CartItemDto(
                        cartItem.getMenuId(),
                        cartItem.getMenuName(),
                        cartItem.getQuantity(),
                        cartItem.getUnitPrice()
                        // CartItemDto에 total price 필드가 있다면 여기에 매핑 추가
                ))
                .collect(Collectors.toList());

        // 4. Feign 클라이언트를 사용하여 주문 서비스에 주문 생성을 요청합니다.
        CreateOrderRequest orderRequest = new CreateOrderRequest(userId, storeId, orderItems);

        // === 로그 추가: 보내려는 orderItems 와 orderRequest 확인 ===
        log.info("장바구니 아이템 변환 결과 (orderItems): {}", orderItems);
        log.info("주문 서비스로 보낼 CreateOrderRequest 객체: {}", orderRequest);
        // ===================================================

        String orderId = null; // 주문 ID를 저장할 변수
        try {
            // === Feign 클라이언트의 createOrder 메서드 호출 ===
            // OpenFeign이 application.yml의 ${order.service.url}을 기본 URL로 사용하여
            // @FeignClient(url=...)와 @PostMapping("/api/orders")를 조합하여
            // http://localhost:8091/api/orders (예시 URL) 로 POST 요청을 보냅니다.
            log.info("주문 서비스 Feign 호출 시도: 사용자 ID={}, 가게 ID={}", userId, storeId); // 호출 전 로그
            orderId = orderServiceClient.createOrder(orderRequest);
            log.info("주문 서비스 Feign 호출 성공, 받은 주문 ID: {}", orderId); // <-- 성공 로그

            // Feign 클라이언트는 기본적으로 2xx 응답이 아니거나 연결 오류 발생 시 예외를 발생시킵니다.
            // 따라서 기존의 if(is2xxSuccessful) 체크는 필요 없어지고, 예외 발생 여부로 성공/실패를 판단합니다.
            // createOrder 메서드가 String을 반환하므로, 반환된 주문 ID의 유효성을 체크합니다.
            if (orderId != null && !orderId.trim().isEmpty()) {
                log.info("Checkout 성공: 주문 ID={}", orderId); // 최종 성공 로그
                return CheckoutResult.success(orderId);
            } else {
                // Feign 호출은 성공했지만, 서비스에서 유효하지 않은 응답(null 또는 빈 문자열)을 반환한 경우
                log.error("주문 서비스 Feign 호출 후 유효하지 않은 결과 받음: {}", orderId); // 에러 로그
                return CheckoutResult.failure("주문 생성 요청 중 오류 발생 (주문 서비스 연동 실패 - 유효하지 않은 응답)");
            }

        } catch (FeignException.FeignClientException.NotFound notFoundException) {
            // Feign 호출 결과 404 Not Found 응답을 받은 경우 (엔드포인트 경로 오류 등)
            log.error("주문 서비스 API Not Found 오류: 상태 코드={}, URL={}",
                    notFoundException.status(), notFoundException.request().url(), notFoundException); // 에러 로그에 상세 정보 포함
            return CheckoutResult.failure("주문 서비스 API 경로 오류 (Not Found)");

        } catch (FeignException.FeignClientException feignException) {
            // Feign 호출 결과 4xx 또는 5xx 등 다른 오류 응답을 받은 경우
            log.error("주문 서비스 Feign 호출 중 FeignClientException 발생: 상태 코드={}, 메시지={}",
                    feignException.status(), feignException.getMessage(), feignException); // 에러 로그에 상태 코드, 메시지 포함
            return CheckoutResult.failure("주문 생성 요청 중 오류 발생 (주문 서비스 응답 오류)");

        } catch (Exception e) { // FeignClientException 외의 다른 예외 (연결 거부, 타임아웃 등 네트워크 오류)
            // === 로그 수정: 발생한 예외의 타입과 메시지를 상세히 로깅합니다. ===
            log.error("주문 서비스 Feign 호출 중 예외 발생: 예외 타입={}, 메시지={}",
                    e.getClass().getName(), e.getMessage(), e); // <-- 예외 타입, 메시지, 전체 스택 트레이스 로깅
            // ===========================================================
            return CheckoutResult.failure("주문 생성 요청 중 오류 발생 (주문 서비스 연동 실패 - 연결/기타 예외)");
        }
    }
}