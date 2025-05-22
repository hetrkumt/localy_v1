package com.localy.payment_service.payment.service;

import com.localy.payment_service.payment.domain.Payment;
import com.localy.payment_service.order.consumer.dto.OrderCreatedEvent;
import com.localy.payment_service.payment.message.dto.PaymentResultEvent;
import com.localy.payment_service.payment.message.PaymentResultProducerConfig;
import com.localy.payment_service.payment.repository.PaymentRepository;
import com.localy.payment_service.virtualAcount.domain.VirtualAccount;

import com.localy.payment_service.virtualAcount.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentProcessorService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentResultProducerConfig paymentResultProducerConfig;

    @Transactional
    public void processOrderCreatedEvent(OrderCreatedEvent orderCreatedEvent) {
        Long orderId = orderCreatedEvent.getOrderId();
        String userId = orderCreatedEvent.getUserId();
        Long storeId = orderCreatedEvent.getStoreId();
        BigDecimal orderAmount = orderCreatedEvent.getTotalAmount();

        System.out.println("결제 처리 시작: 주문 ID=" + orderId);

        Optional<VirtualAccount> customerAccount = virtualAccountRepository.findByUserId(userId);
        if (customerAccount.isEmpty()) {
            handlePaymentFailure(orderId, orderAmount, null);
            return;
        }

        if (!isBalanceSufficient(customerAccount.orElse(null), orderAmount)) {
            handlePaymentFailure(orderId, orderAmount, null);
            return;
        }

        debitCustomerAccount(customerAccount.orElse(null), orderAmount);

        Optional<VirtualAccount> storeOwnerAccount = virtualAccountRepository.findByStoreId(storeId);
        if (storeOwnerAccount.isEmpty()) {
            handlePaymentFailure(orderId, orderAmount, customerAccount.orElse(null)); // 롤백 고려
            return;
        }

        creditStoreOwnerAccount(storeOwnerAccount.orElse(null), orderAmount);

        Payment payment = savePaymentSuccess(orderId, orderAmount);
        sendPaymentResult(orderId, payment.getPaymentId(), "APPROVED");
    }

    private VirtualAccount retrieveCustomerAccount(String userId) {
        Optional<VirtualAccount> account = virtualAccountRepository.findByUserId(userId);
        if (account.isEmpty()) {
            System.err.println("손님 가상 계좌를 찾을 수 없습니다: 사용자 ID=" + userId);
        }
        return account.orElse(null);
    }

    private boolean isBalanceSufficient(VirtualAccount account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            System.err.println("잔액이 부족합니다: 사용자 ID=" + account.getUserId() + ", 잔액=" + account.getBalance() + ", 주문 금액=" + amount);
            return false;
        }
        return true;
    }

    private void debitCustomerAccount(VirtualAccount account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(amount));
        virtualAccountRepository.save(account);
        System.out.println("손님 가상 계좌 잔액 차감 완료: 사용자 ID=" + account.getUserId() + ", 잔액=" + account.getBalance());
    }

    private VirtualAccount retrieveStoreOwnerAccount(Long storeId) {
        Optional<VirtualAccount> account = virtualAccountRepository.findByStoreId(storeId);
        if (account.isEmpty()) {
            System.err.println("가계 주인 가상 계좌를 찾을 수 없습니다: 가계 ID=" + storeId);
        }
        return account.orElse(null);
    }

    private void creditStoreOwnerAccount(VirtualAccount account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        virtualAccountRepository.save(account);
        System.out.println("가계 주인 가상 계좌 잔액 증가 완료: 가계 ID=" + account.getStoreId() + ", 잔액=" + account.getBalance());
    }

    private Payment savePaymentSuccess(Long orderId, BigDecimal totalAmount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentStatus("APPROVED")
                .totalAmount(totalAmount)
                .paymentDate(LocalDateTime.now())
                .build();
        Payment savedPayment = paymentRepository.save(payment);
        System.out.println("결제 성공 정보 저장 완료: 주문 ID=" + orderId + ", 결제 ID=" + savedPayment.getPaymentId()); // === 로그 추가: 성공 저장 알림 ===
        return savedPayment;
    }


    private void savePaymentFailure(Long orderId, BigDecimal totalAmount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentStatus("REJECTED")
                .totalAmount(totalAmount)
                .build();
        paymentRepository.save(payment);
        System.err.println("결제 실패 정보 저장 완료: 주문 ID=" + orderId);
    }

    private void sendPaymentResult(Long orderId, Long paymentId, String paymentStatus) {
        PaymentResultEvent paymentResultEvent = PaymentResultEvent.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .paymentStatus(paymentStatus)
                .build();

        // === 로그 추가: PaymentResultEvent 생성 및 발행 시도 알림 ===
        System.out.println("PaymentService: PaymentResultEvent 생성 및 발행 시도 - 주문 ID: " + orderId + ", 상태: " + paymentStatus);
        // =======================================================

        paymentResultProducerConfig.sendPaymentResult(paymentResultEvent);
    }

    private void handlePaymentFailure(Long orderId, BigDecimal orderAmount, VirtualAccount customerAccount) {
        savePaymentFailure(orderId, orderAmount);
        System.err.println("결제 실패 정보 저장 완료: 주문 ID=" + orderId); // 이 로그는 이미 있군요.

        // === 로그 추가: 결제 실패 시 PaymentResultEvent 발행 시도 알림 ===
        System.out.println("PaymentService: 결제 실패 처리 완료, PaymentResultEvent 발행 시도 - 주문 ID: " + orderId + ", 상태: REJECTED");
        // ==========================================================

        sendPaymentResult(orderId, null, "REJECTED"); // 여기서 sendPaymentResult 호출
        if (customerAccount != null) {
            // 롤백 로직 구현 (추후)
            System.err.println("결제 실패로 인해 손님 계좌 롤백 필요 (미구현): 주문 ID=" + orderId);
        }
    }
}