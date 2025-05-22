package com.localy.order_service.order.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference; // Jackson 어노테이션은 유지 (JSON 직렬화용)
import lombok.*;
import org.springframework.data.annotation.Id; // Spring Data 어노테이션 사용
import org.springframework.data.annotation.Transient; // DB 매핑 제외 필드
import org.springframework.data.relational.core.mapping.Column; // R2DBC용 Column 어노테이션 (필요시 사용)
import org.springframework.data.relational.core.mapping.Table;  // Spring Data R2DBC 어노테이션 사용

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Table("orders") // Spring Data R2DBC 테이블 매핑
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id // Spring Data 어노테이션 사용
    // @GeneratedValue(strategy = GenerationType.IDENTITY) // R2DBC에서는 DB 스키마(SERIAL 등)에 의존, 엔티티에 별도 설정 불필요
    private Long orderId; // DB 컬럼명과 일치하거나 @Column으로 매핑

    @Column("user_id") // 명시적으로 컬럼명 매핑 (Java 필드명과 DB 컬럼명이 다를 경우)
    private String userId;

    @Column("store_id")
    private Long storeId;

    // R2DBC에서는 직접적인 @OneToMany 컬렉션 매핑을 지원하지 않음.
    // 서비스 계층에서 별도로 조회하여 채워야 함.
    @Transient // DB에 매핑되지 않는 필드
    @JsonManagedReference // JSON 직렬화 시 순환 참조 방지용 (필요시 유지)
    @Builder.Default // Lombok Builder 사용 시 초기화 유지
    private List<OrderLineItem> orderLineItems = new ArrayList<>();

    @Column("order_date")
    private LocalDateTime orderDate;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("order_status")
    private String orderStatus;

    @Column("payment_id") // DB 컬럼명과 일치하는지 확인
    private Long paymentId;

    @Column("created_at") // DB 스키마의 created_at 컬럼에 매핑
    private LocalDateTime createdAt; // @CreatedDate는 R2DBC Auditing 설정 시 동작 (별도 설정 필요)

    // R2DBC에서는 엔티티 내 계산 메서드는 그대로 사용 가능
    public BigDecimal calculateTotalAmount() {
        if (this.orderLineItems == null) {
            return BigDecimal.ZERO;
        }
        return this.orderLineItems.stream()
                .map(OrderLineItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
