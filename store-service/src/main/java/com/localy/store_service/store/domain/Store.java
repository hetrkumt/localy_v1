package com.localy.store_service.store.domain;

import com.localy.store_service.review.domain.Review;
import com.localy.store_service.menu.domain.Menu;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;


@Table("stores") // R2DBC 테이블 매핑 어노테이션
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Store {

    @Id // 기본 키 필드임을 나타냅니다. R2DBC는 DB 설정에 따라 ID 생성 관리.
    // @GeneratedValue(strategy = GenerationType.IDENTITY) // JPA 어노테이션이므로 제거
    private Long id; // 가게 고유 식별자

    private String name; // 가게 이름
    private String description; // 가게 설명
    private String address; // 가게 주소
    private Double latitude; // 가게 위치 위도
    private Double longitude; // 가게 위치 경도
    private String phone; // 가게 연락처
    private String openingHours; // 영업 시간 정보

    // Enum 타입 필드 (R2DBC는 보통 문자열로 DB에 저장)
    private StoreStatus status; // StoreStatus Enum 사용
    private StoreCategory category; // StoreCategory Enum 사용

    // --- Auditing 필드 (Spring Data 어노테이션 사용) ---
    @CreatedDate // 엔티티 생성 시 시간 자동 기록
    // @Column(nullable = false, updatable = false) // JPA 어노테이션이므로 제거
    private LocalDateTime createdAt; // 생성 시간

    @LastModifiedDate // 엔티티 수정 시 시간 자동 기록
    private LocalDateTime updatedAt; // 최종 수정 시간
    // ---------------------------------------------------

    // --- R2DBC는 JPA 연관관계 매핑을 직접 지원하지 않음 ---
    // 연관관계 필드는 DB 매핑 대상에서 제외합니다.
    // 필요하다면 Service 레이어에서 별도 쿼리로 연관 데이터(메뉴, 리뷰)를 가져와 채워야 합니다.
    @Transient // 이 필드는 R2DBC가 DB 컬럼으로 매핑하지 않도록 지정
    @ToString.Exclude // toString()에서 제외
    private List<Menu> menus; // 이 가게에 속한 메뉴 목록 (DB 매핑 안됨)

    @Transient // 이 필드는 R2DBC가 DB 컬럼으로 매핑하지 않도록 지정
    @ToString.Exclude // toString()에서 제외
    private List<Review> reviews; // 이 가게에 대한 리뷰 목록 (DB 매핑 안됨)

    // JPA 연관관계 편의 메서드도 제거합니다.
}