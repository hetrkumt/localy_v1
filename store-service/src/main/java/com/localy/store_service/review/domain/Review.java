package com.localy.store_service.review.domain;

import com.localy.store_service.store.domain.Store; // Store 엔티티 임포트 (Transient 필드용)
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime; // LocalDateTime 임포트
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

    // 외래 키 ID 필드는 직접 관리 (R2DBC는 JPA ManyToOne을 직접 지원하지 않음)
    private Long storeId; // 이 리뷰가 달린 가게의 ID
    private String userId; // 리뷰 작성자의 사용자 ID
    private Integer rating; // 평점 (Integer 타입)
    private String comment; // 리뷰 내용

    // --- Auditing 필드 ---
    // @CreatedDate와 @LastModifiedDate 어노테이션은 Spring Data R2DBC Auditing 설정 시
    // save/update 작업에서 자동으로 현재 시간을 채워줍니다.
    @CreatedDate
    private LocalDateTime createdAt; // 생성 시간

    @LastModifiedDate
    private LocalDateTime updatedAt; // 최종 수정 시간
    // ---------------------

    // 연관관계 필드는 DB 매핑 대상에서 제외 (@Transient 사용)
    @Transient // 이 필드는 R2DBC가 DB 컬럼으로 매핑하지 않도록 지정
    @ToString.Exclude // toString()에서 제외 (스택 오버플로우 방지)
    private Store store; // 이 리뷰의 대상 가게 엔티티 (DB 매핑 안됨)

    // JPA 연관관계 편의 메서드 제거
}
