package com.localy.order_service.order.domain;

import com.fasterxml.jackson.annotation.JsonBackReference; // Jackson 어노테이션은 유지 (JSON 직렬화용)
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id; // Spring Data 어노테이션 사용
import org.springframework.data.relational.core.mapping.Column; // R2DBC용 Column 어노테이션 (필요시 사용)
import org.springframework.data.relational.core.mapping.Table;  // Spring Data R2DBC 어노테이션 사용

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Table("order_line_items") // Spring Data R2DBC 테이블 매핑
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItem {

    @Id // Spring Data 어노테이션 사용
    // @GeneratedValue(strategy = GenerationType.IDENTITY) // R2DBC에서는 DB 스키마(SERIAL 등)에 의존, 엔티티에 별도 설정 불필요
    private Long orderItemId; // DB 컬럼명과 일치하거나 @Column("order_item_id") 등으로 매핑

    // JPA의 @ManyToOne, @JoinColumn 대신, 부모 엔티티의 ID를 직접 저장하는 필드를 사용합니다.
    // DB 스키마의 외래 키 컬럼명과 일치하도록 @Column 어노테이션을 사용합니다.
    @Column("order_id")
    private Long orderIdFk; // Order 엔티티의 ID를 저장 (필드명을 orderId로 해도 무방하나, 명확성을 위해 Fk 접미사 사용 가능)

    @Column("menu_id") // DB 컬럼명과 일치
    private String menuId;

    @Column("menu_name") // DB 컬럼명과 일치
    private String menuName;

    @Column("quantity") // DB 컬럼명과 일치
    private Integer quantity;

    @Column("unit_price") // DB 컬럼명과 일치
    private BigDecimal unitPrice;

    @Column("total_price") // DB 컬럼명과 일치
    private BigDecimal totalPrice;

    @Column("created_at") // DB 컬럼명과 일치
    private LocalDateTime createdAt; // @CreatedDate는 R2DBC Auditing 설정 시 동작 (별도 설정 필요)

    // R2DBC에서는 엔티티 간 직접 참조 필드(예: private Order order;)는 @Transient로 선언하고
    // 서비스 계층에서 필요에 따라 ID를 사용해 조회하여 채웁니다.
    // 여기서는 JSON 직렬화 시 순환 참조를 피하기 위한 @JsonBackReference와 함께 사용될 수 있으나,
    // 해당 'Order order' 필드가 없으므로 @JsonBackReference도 여기서는 불필요합니다.
    // 만약 Order 객체를 이 클래스 내에 @Transient 필드로 두고 싶다면,
    // @Transient
    // @JsonBackReference // Order 엔티티의 orderLineItems 필드에 @JsonManagedReference가 있다면 사용
    // private Order order;
    // 와 같이 할 수 있습니다. 현재는 Order 객체 필드가 없으므로 관련 어노테이션도 제거합니다.
}
