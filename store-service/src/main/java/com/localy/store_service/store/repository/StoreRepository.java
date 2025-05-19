package com.localy.store_service.store.repository;

import com.localy.store_service.store.domain.Store;
// import com.localy.store_service.store.domain.StoreCategory; // 필요시 사용
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Sort 임포트
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import reactor.core.publisher.Flux;
import java.util.List; // List 임포트

public interface StoreRepository extends R2dbcRepository<Store, Long>, ReactiveQueryByExampleExecutor<Store> {

    Flux<Store> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // Flux<Store> findByCategory(StoreCategory category, Pageable pageable);
    // Flux<Store> findByNameContainingIgnoreCaseAndCategory(String name, StoreCategory category, Pageable pageable);

    // ID 목록과 Sort 정보를 받아 가게 목록을 반환하는 메서드 (페이지네이션은 서비스에서 수동 처리)
    Flux<Store> findAllByIdIn(List<Long> ids, Sort sort);

    // 만약 Pageable을 직접 지원하는 findAllByIdIn이 있다면 그것을 사용해도 좋습니다.
    // Flux<Store> findAllByIdIn(List<Long> ids, Pageable pageable);
}
