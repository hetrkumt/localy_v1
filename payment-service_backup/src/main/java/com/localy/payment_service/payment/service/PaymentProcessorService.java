package com.localy.payment_service.payment.service;

import com.localy.payment_service.payment.domain.Payment;
import com.localy.payment_service.payment.domain.VirtualAccount;
import com.localy.payment_service.order.consumer.dto.OrderCreatedEvent;
import com.localy.payment_service.payment.message.dto.PaymentResultEvent;
import com.localy.payment_service.payment.message.PaymentResultProducerConfig;
import com.localy.payment_service.payment.repository.PaymentRepository;
import com.localy.payment_service.payment.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentProcessorService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentResultProducerConfig paymentResultProducerConfig;

    // 실패 사유 코드 정의 (예시)
    private static final String REASON_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    private static final String REASON_CUSTOMER_ACCOUNT_NOT_FOUND = "CUSTOMER_ACCOUNT_NOT_FOUND";
    private static final String REASON_STORE_ACCOUNT_NOT_FOUND = "STORE_ACCOUNT_NOT_FOUND";
    private static final String REASON_PAYMENT_PROCESSING_ERROR = "PAYMENT_PROCESSING_ERROR";


    @Transactional
    public void processOrderCreatedEvent(OrderCreatedEvent orderCreatedEvent) {
        Long orderId = orderCreatedEvent.getOrderId();
        String userId = orderCreatedEvent.getUserId();
        String storeId = orderCreatedEvent.getStoreId();
        BigDecimal orderAmount = orderCreatedEvent.getTotalAmount();

        System.out.println("결제 처리 시작: 주문 ID=" + orderId + ", 사용자 ID=" + userId + ", 가게 ID=" + storeId + ", 금액=" + orderAmount);

        VirtualAccount customerAccount = virtualAccountRepository.findByUserId(userId);
        if (customerAccount == null) {
            System.err.println("고객 계좌 없음: 사용자 ID=" + userId);
            handlePaymentFailure(orderId, orderAmount, null, REASON_CUSTOMER_ACCOUNT_NOT_FOUND);
            // 여기서 RuntimeException을 발생시켜 트랜잭션 롤백을 유도할 수 있습니다.
            // throw new RuntimeException("고객 계좌를 찾을 수 없습니다: " + userId);
            return;
        }

        if (!isBalanceSufficient(customerAccount, orderAmount)) {
            System.err.println("잔액 부족: 사용자 ID=" + userId);
            handlePaymentFailure(orderId, orderAmount, null, REASON_INSUFFICIENT_FUNDS);
            // 여기서 RuntimeException을 발생시켜 트랜잭션 롤백을 유도할 수 있습니다.
            // throw new RuntimeException("고객 계좌 잔액이 부족합니다: " + userId);
            return;
        }

        // 고객 계좌에서 출금
        try {
            debitCustomerAccount(customerAccount, orderAmount);
        } catch (Exception e) {
            System.err.println("고객 계좌 출금 중 오류 발생: 사용자 ID=" + userId + ", 오류: " + e.getMessage());
            handlePaymentFailure(orderId, orderAmount, null, REASON_PAYMENT_PROCESSING_ERROR); // 출금 실패 시에도 결제 실패 처리
            // throw new RuntimeException("고객 계좌 출금 중 오류: " + e.getMessage(), e); // 트랜잭션 롤백 유도
            return;
        }


        VirtualAccount storeOwnerAccount = virtualAccountRepository.findByStoreId(storeId);
        if (storeOwnerAccount == null) {
            System.err.println("가게 주인 계좌 없음: 가게 ID=" + storeId);
            // 고객 계좌 출금 롤백을 위해 예외 발생
            handlePaymentFailure(orderId, orderAmount, customerAccount, REASON_STORE_ACCOUNT_NOT_FOUND); // 실패 이벤트는 발행
            throw new RuntimeException("가게 주인 계좌를 찾을 수 없습니다: " + storeId + ". 고객 계좌 롤백 필요.");
            // return; // 도달하지 않음
        }

        // 가게 주인 계좌에 입금
        try {
            creditStoreOwnerAccount(storeOwnerAccount, orderAmount);
        } catch (Exception e) {
            System.err.println("가게 주인 계좌 입금 중 오류 발생: 가게 ID=" + storeId + ", 오류: " + e.getMessage());
            handlePaymentFailure(orderId, orderAmount, customerAccount, REASON_PAYMENT_PROCESSING_ERROR); // 입금 실패 시에도 결제 실패 이벤트 발행
            throw new RuntimeException("가게 주인 계좌 입금 중 오류: " + e.getMessage() + ". 고객 계좌 롤백 필요.", e); // 트랜잭션 롤백 유도
            // return; // 도달하지 않음
        }


        Payment payment = savePaymentSuccess(orderId, orderAmount);
        sendPaymentResult(orderId, payment.getPaymentId(), "APPROVED", null); // 성공 시 failureReason은 null
        System.out.println("결제 처리 성공 및 이벤트 발행 완료: 주문 ID=" + orderId);
    }

    private boolean isBalanceSufficient(VirtualAccount account, BigDecimal amount) {
        boolean sufficient = account.getBalance().compareTo(amount) >= 0;
        if (!sufficient) {
            System.err.println("잔액이 부족합니다: 사용자 ID=" + account.getUserId() + ", 잔액=" + account.getBalance() + ", 주문 금액=" + amount);
        }
        return sufficient;
    }

    private void debitCustomerAccount(VirtualAccount account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(amount));
        virtualAccountRepository.save(account);
        System.out.println("손님 가상 계좌 잔액 차감 완료: 사용자 ID=" + account.getUserId() + ", 새 잔액=" + account.getBalance());
    }

    private void creditStoreOwnerAccount(VirtualAccount account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        virtualAccountRepository.save(account);
        System.out.println("가게 주인 가상 계좌 잔액 증가 완료: 가게 ID=" + account.getStoreId() + ", 새 잔액=" + account.getBalance());
    }

    private Payment savePaymentSuccess(Long orderId, BigDecimal totalAmount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentStatus("APPROVED")
                .totalAmount(totalAmount)
                .paymentDate(LocalDateTime.now()) // 실제 결제 완료 시간
                // .transactionId() // 실제 PG 연동 시 거래 ID 설정
                .build();
        Payment savedPayment = paymentRepository.save(payment);
        System.out.println("결제 성공 정보 저장 완료: 주문 ID=" + orderId + ", 결제 ID=" + savedPayment.getPaymentId());
        return savedPayment;
    }

    // 실패 정보 저장 메서드 (실패 사유는 여기서 직접 설정하지 않고, handlePaymentFailure에서 받음)
    private void savePaymentFailureRecord(Long orderId, BigDecimal totalAmount, String paymentStatus) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentStatus(paymentStatus) // "REJECTED" 또는 다른 실패 상태
                .totalAmount(totalAmount)
                // .paymentDate(null) // 실패 시 paymentDate는 null일 수 있음
                .build();
        paymentRepository.save(payment);
        System.err.println("결제 " + paymentStatus + " 정보 저장 완료: 주문 ID=" + orderId);
    }

    // Kafka 이벤트 발행 메서드 (실패 사유 포함)
    private void sendPaymentResult(Long orderId, Long paymentId, String paymentStatus, String failureReason) {
        PaymentResultEvent paymentResultEvent = PaymentResultEvent.builder()
                .orderId(orderId)
                .paymentId(paymentId) // 성공 시에만 paymentId가 있을 수 있음
                .paymentStatus(paymentStatus)
                .failureReason(failureReason) // 실패 사유 전달
                .build();
        System.out.println("PaymentService: PaymentResultEvent 생성 및 발행 시도 - 이벤트: " + paymentResultEvent);
        paymentResultProducerConfig.sendPaymentResult(paymentResultEvent);
    }

    // 중앙화된 결제 실패 처리 메서드
    private void handlePaymentFailure(Long orderId, BigDecimal orderAmount, VirtualAccount customerAccountToPotentiallyRollback, String failureReason) {
        savePaymentFailureRecord(orderId, orderAmount, "REJECTED"); // 실패 기록 저장
        sendPaymentResult(orderId, null, "REJECTED", failureReason); // 실패 이벤트 발행 (실패 사유 포함)

        // 고객 계좌 출금 롤백은 @Transactional에 의해 자동으로 처리되도록 유도 (RuntimeException 발생 시)
        // 만약 여기서 RuntimeException을 던지지 않는다면, 수동 롤백 로직이 필요하지만 권장되지 않음.
        // if (customerAccountToPotentiallyRollback != null) {
        //     System.err.println("결제 실패로 인해 손님 계좌 롤백 필요 (현재는 예외 발생으로 @Transactional에 위임): 주문 ID=" + orderId);
        // }
    }
}
