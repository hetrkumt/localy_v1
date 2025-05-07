package com.localy.store_service.menu.repository;


import com.localy.store_service.menu.domain.Menu;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository; // Repository 어노테이션 임포트
import reactor.core.publisher.Flux;


@Repository // 이 인터페이스가 Repository 컴포넌트임을 나타냅니다.
public interface MenuRepository extends R2dbcRepository<Menu, Long> {
    // R2dbcRepository를 상속받아 기본적인 Reactive CRUD 기능을 자동 제공합니다.
    // Mono<Menu> findById(Long id);
    // Flux<Menu> findAll();
    // Mono<Menu> save(Menu menu);
    // Mono<Void> deleteById(Long id);
    // Mono<Boolean> existsById(Long id);
    // Mono<Long> count();

    // --- Custom Reactive 쿼리 메서드 ---
    // 특정 storeId를 가진 모든 Menu 엔티티를 조회합니다.
    Flux<Menu> findByStoreId(Long storeId);
    // ------------------------------------
}