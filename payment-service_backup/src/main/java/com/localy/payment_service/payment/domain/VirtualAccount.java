package com.localy.payment_service.payment.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "virtual-account")
@Builder
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @Column(nullable = false)
    private String userId; // 사용자 ID

    private String storeId; // 가계 ID (nullable = true 로 설정할 수도 있습니다.)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}