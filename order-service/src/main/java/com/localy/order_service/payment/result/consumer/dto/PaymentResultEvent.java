package com.localy.order_service.payment.result.consumer.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PaymentResultEvent {
    private Long orderId;
    private Long paymentId;
    private String paymentStatus;
}