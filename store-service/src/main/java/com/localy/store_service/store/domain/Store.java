package com.localy.store_service.store.domain;

import com.localy.store_service.review.domain.Review;
import com.localy.store_service.menu.domain.Menu;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;


@Table("stores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    private Long id;
    private String ownerId; // 가게 주인의 사용자 ID 필드
    private String name;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String openingHours;
    private StoreStatus status; // Enum 타입 필드 (R2DBC는 보통 문자열로 DB에 저장)
    private StoreCategory category; // StoreCategory Enum 사용

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Transient
    @ToString.Exclude
    private List<Menu> menus;

    @Transient
    @ToString.Exclude
    private List<Review> reviews;

    // JPA 연관관계 편의 메서드 제거
}