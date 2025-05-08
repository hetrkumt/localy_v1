package com.localy.store_service.menu.repository;


import com.localy.store_service.menu.domain.Menu;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository; // Repository 어노테이션 임포트
import reactor.core.publisher.Flux;


@Repository // 이 인터페이스가 Repository 컴포넌트임을 나타냅니다.
public interface MenuRepository extends R2dbcRepository<Menu, Long> {

    Flux<Menu> findByStoreId(Long storeId);
    // ------------------------------------
}