package com.localy.store_service.store.repository;

import com.localy.store_service.store.domain.Store;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository; // Repository 어노테이션 임포트
import reactor.core.publisher.Flux;

@Repository // 이 인터페이스가 Repository 컴포넌트임을 나타냅니다.
public interface StoreRepository extends R2dbcRepository<Store, Long> { // R2DBC Repository로 변경
    // R2dbcRepository는 기본적으로 다음 메서드들을 Reactive 타입으로 제공:
    // Mono<Store> findById(Long id);
    // Flux<Store> findAll();
    // Mono<Store> save(Store store);
    // Mono<Void> deleteById(Long id);
    // Mono<Boolean> existsById(Long id);
    // Mono<Long> count();

    // --- 새로운 기능: 가게 이름을 포함하는 모든 가게 조회 (대소문자 구분 없음) ---
    // R2DBC Repository도 쿼리 메서드 네이밍 규칙을 지원하며 Reactive 타입을 반환합니다.
    Flux<Store> findByNameContainingIgnoreCase(String name);
    // ------------------------------------------------------------------
}