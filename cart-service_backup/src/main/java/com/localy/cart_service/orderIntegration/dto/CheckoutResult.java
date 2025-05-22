package com.localy.cart_service.orderIntegration.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 주문 요청 결과를 담는 클래스
@Getter // 필드 값에 접근하기 위한 Getter 자동 생성
@RequiredArgsConstructor // final 필드를 인자로 받는 생성자 자동 생성
public class CheckoutResult {
    private final boolean success; // 주문 요청 성공 여부
    private final String orderId; // 성공 시 주문 ID (실패 시 null)
    private final String errorMessage; // 실패 시 오류 메시지 (성공 시 null)

    // 성공 결과를 생성하는 팩토리 메서드
    public static CheckoutResult success(String orderId) {
        return new CheckoutResult(true, orderId, null);
    }

    // 실패 결과를 생성하는 팩토리 메서드
    public static CheckoutResult failure(String errorMessage) {
        return new CheckoutResult(false, null, errorMessage);
    }
}