package com.localy.store_service.store.service;

import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.repository.StoreRepository;
import org.springframework.stereotype.Service; // Service 어노테이션 임포트
import reactor.core.publisher.Flux; // Reactor Flux 임포트
import reactor.core.publisher.Mono; // Reactor Mono 임포트
// import reactor.core.scheduler.Schedulers; // R2DBC 사용 시 Repository 호출에 필요 없음

import java.util.NoSuchElementException; // 데이터가 없을 경우 예외 처리용

@Service // 이 클래스가 Service 컴포넌트임을 나타냅니다.
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    // 모든 가게 목록 조회
    public Flux<Store> findAllStores() {
        System.out.println("--- StoreService: findAllStores 호출 (R2DBC) ---");
        return storeRepository.findAll() // R2dbcRepository의 findAll()은 Flux<Store>를 반환
                .doOnComplete(() -> System.out.println("--- StoreService: findAllStores 완료 ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }

    // ID로 특정 가게 조회
    public Mono<Store> findStoreById(Long id) {
        System.out.println("--- StoreService: findStoreById 호출 (ID: " + id + ", R2DBC) ---");
        return storeRepository.findById(id) // R2dbcRepository의 findById()는 Mono<Store>를 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id))) // Mono가 비어있으면 (데이터 없으면) 예외 발행
                .doOnSuccess(store -> System.out.println("--- StoreService: 가게 찾음 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: findStoreById 오류 - " + e.getMessage() + " ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }

    // 새로운 가게 생성
    public Mono<Store> createStore(Store store) {
        System.out.println("--- StoreService: createStore 호출 (Name: " + store.getName() + ", R2DBC) ---");
        // R2DBC는 ID 자동 생성 후 save 메서드의 반환 객체에 ID를 채워줌
        return storeRepository.save(store) // R2dbcRepository의 save()는 Mono<Store>를 반환
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 생성 완료 (ID: " + savedStore.getId() + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: createStore 오류 - " + e.getMessage() + " ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }

    // --- 특정 가게 정보 수정
    // 순서: 찾기 -> 업데이트 -> 저장
    public Mono<Store> updateStore(Long id, Store updatedStore) {
        System.out.println("--- StoreService: updateStore 호출 (ID: " + id + ", R2DBC) ---");
        // 1. 먼저 기존 가게를 ID로 찾습니다. (findStoreById 메서드를 재사용하거나 findById를 직접 사용)
        return storeRepository.findById(id) // Mono<Store> 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id))) // 없으면 예외 발행
                .flatMap(existingStore -> { // Mono<Store>가 데이터를 발행하면 (가게를 찾으면) 다음 단계 실행
                    System.out.println("--- StoreService: 수정할 가게 찾음 (ID: " + id + ") --- 업데이트 시작 ---");
                    // 2. 기존 가게 엔티티의 필드들을 업데이트할 데이터로 채웁니다.
                    existingStore.setName(updatedStore.getName());
                    existingStore.setDescription(updatedStore.getDescription());
                    existingStore.setAddress(updatedStore.getAddress());
                    existingStore.setLatitude(updatedStore.getLatitude());
                    existingStore.setLongitude(updatedStore.getLongitude());
                    existingStore.setPhone(updatedStore.getPhone());
                    existingStore.setOpeningHours(updatedStore.getOpeningHours());
                    existingStore.setStatus(updatedStore.getStatus()); // Enum 타입 복사
                    existingStore.setCategory(updatedStore.getCategory()); // Enum 타입 복사
                    // Note: R2DBC Auditing 설정이 되어 있다면 createdAt, updatedAt은 자동으로 처리됩니다.

                    // 3. 업데이트된 엔티티를 저장합니다. (save 메서드가 수정 역할)
                    return storeRepository.save(existingStore); // save는 저장 후 업데이트된 Mono<Store> 반환
                })
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 수정 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: updateStore 오류 - " + e.getMessage() + " ---"));
    }

    // --- 특정 가게 삭제
    // 순서: 존재 확인 (선택 사항이나 안전) -> 삭제
    public Mono<Void> deleteStore(Long id) {
        System.out.println("--- StoreService: deleteStore 호출 (ID: " + id + ", R2DBC) ---");
        // R2dbcRepository의 deleteById는 Mono<Void>를 반환하며 존재하지 않아도 오류 나지 않음.
        // 삭제 전에 존재하는지 확인하고 싶다면 existsById 사용
        return storeRepository.existsById(id) // 해당 ID의 가게가 존재하는지 확인 (Mono<Boolean> 반환)
                .flatMap(exists -> { // 존재 여부 결과에 따라 다음 단계를 실행 (true/false)
                    if (exists) {
                        System.out.println("--- StoreService: 삭제할 가게 존재 (ID: " + id + ") --- 삭제 시작 ---");
                        // 가게가 존재하면 deleteById 호출 (Mono<Void> 반환)
                        return storeRepository.deleteById(id)
                                .doOnSuccess(aVoid -> System.out.println("--- StoreService: 가게 삭제 완료 (ID: " + id + ") ---"))
                                .doOnError(e -> System.err.println("--- StoreService: deleteStore 오류 중 삭제 실패 - " + e.getMessage() + " ---"));
                    } else {
                        // 가게가 존재하지 않으면 NoSuchElementException을 발행하여 스트림을 종료
                        System.err.println("--- StoreService: 삭제할 가게 찾을 수 없음 (ID: " + id + ") ---");
                        return Mono.error(new NoSuchElementException("Store not found with ID: " + id));
                    }
                })
                .onErrorResume(e -> {
                    // existsById 또는 deleteById 체인 중 발생한 오류를 잡습니다.
                    System.err.println("--- StoreService: deleteStore 오류 - " + e.getMessage() + " ---");
                    // 오류를 컨트롤러로 다시 전달합니다.
                    return Mono.error(e);
                });
    }

    // 가게 이름으로 검색
    public Flux<Store> searchStoresByName(String name) {
        System.out.println("--- StoreService: searchStoresByName 호출 (검색어: " + name + ", R2DBC) ---");
        // R2dbcRepository의 쿼리 메서드 호출은 Flux<Store>를 반환
        return storeRepository.findByNameContainingIgnoreCase(name)
                .doOnComplete(() -> System.out.println("--- StoreService: searchStoresByName 완료 ---"))
                .doOnError(e -> System.err.println("--- StoreService: searchStoresByName 오류 - " + e.getMessage() + " ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }
}