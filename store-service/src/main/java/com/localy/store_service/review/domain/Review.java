package com.localy.store_service.review.domain;

import com.localy.store_service.store.domain.Store;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id; // Spring Data Id 임포트
import org.springframework.data.relational.core.mapping.Table; // Spring Data R2DBC Table 임포트
import org.springframework.data.annotation.CreatedDate; // Spring Data Auditing 임포트
import org.springframework.data.annotation.LastModifiedDate; // Spring Data Auditing 임포트
import org.springframework.data.annotation.Transient; // Spring Data Transient 임포트

@Table("reviews") // R2DBC 테이블 매핑 어노테이션
@Data
@NoArgsConstructor
@AllArgsConstructor
// @EntityListeners(AuditingEntityListener.class) // JPA 어노테이션이므로 제거
public class Review {

    @Id // 기본 키 필드
    // @GeneratedValue(strategy = GenerationType.IDENTITY) // JPA 어노테이션이므로 제거
    private Long id; // 리뷰 고유 식별자

    // --- R2DBC는 JPA ManyToOne을 직접 지원하지 않음 ---
    // 외래 키 ID 필드는 직접 관리
    private Long storeId; // 이 리뷰가 달린 가게의 ID

    private String userId; // 리뷰 작성자의 사용자 ID
    private Integer rating; // 평점
    private String comment; // 리뷰 내용

    // --- Auditing 필드 ---
    @CreatedDate
    // @Column(nullable = false, updatable = false) // JPA 어노테이션이므로 제거
    private LocalDateTime createdAt; // 생성 시간

    @LastModifiedDate
    private LocalDateTime updatedAt; // 최종 수정 시간
    // ---------------------

    // --- R2DBC는 JPA ManyToOne을 직접 지원하지 않음 ---
    // 연관관계 필드는 DB 매핑 대상에서 제외 (개념적 연결 필요시)
    // @ManyToOne(...) // JPA 어노테이션이므로 제거
    @Transient // 이 필드는 R2DBC가 DB 컬럼으로 매핑하지 않도록 지정
    @ToString.Exclude // toString()에서 제외
    private Store store; // 이 리뷰의 대상 가게 엔티티 (DB 매핑 안됨)

    // JPA 연관관계 편의 메서드 제거
}