package com.localy.payment_service.payment.repository;

import com.localy.payment_service.payment.domain.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {
    VirtualAccount findByUserId(String userId);
    VirtualAccount findByStoreId(String storeId); // 필요에 따라 추가
}