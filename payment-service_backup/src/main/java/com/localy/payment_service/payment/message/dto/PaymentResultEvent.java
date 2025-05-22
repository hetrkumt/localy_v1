// 파일 위치: com.localy.payment_service.payment.message.dto.PaymentResultEvent.java
package com.localy.payment_service.payment.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString; // ToString 추가 (로깅 시 유용)

import java.math.BigDecimal; // 필요시 사용

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString // 로그 확인을 위해 추가
public class PaymentResultEvent {
    private Long orderId;
    private Long paymentId; // 결제 성공 시에만 값이 있을 수 있음
    private String paymentStatus; // 예: "APPROVED", "REJECTED"
    private String failureReason; // 결제 실패 사유 (새로 추가된 필드)
    // private BigDecimal amount; // 필요하다면 결제 금액도 포함 가능
}
