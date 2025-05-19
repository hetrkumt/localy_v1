package com.localy.store_service.store.service; // 적절한 서비스 패키지 사용

// 필요한 임포트
import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor; // Lombok RequiredArgsConstructor 임포트
import org.springframework.stereotype.Service; // Service 어노테이션 임포트

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException; // 예외 임포트
import java.lang.SecurityException; // SecurityException 임포트
import java.lang.IllegalArgumentException; // IllegalArgumentException 임포트
import java.time.LocalDateTime;


@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 주입 (storeRepository)
public class StoreService {

    private final StoreRepository storeRepository;

    // 모든 가게 목록 조회 (권한 확인 필요 없음)
    public Flux<Store> findAllStores() {
        System.out.println("--- StoreService: findAllStores 호출 (R2DBC) ---");
        return storeRepository.findAll() // R2dbcRepository의 findAll()은 Flux<Store>를 반환
                .doOnComplete(() -> System.out.println("--- StoreService: findAllStores 완료 ---"));
    }

    // ID로 특정 가게 조회 (권한 확인 필요 없음)
    public Mono<Store> findStoreById(Long id) {
        System.out.println("--- StoreService: findStoreById 호출 (ID: " + id + ", R2DBC) ---");
        return storeRepository.findById(id) // R2dbcRepository의 findById()는 Mono<Store>를 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id))) // Mono가 비어있으면 (데이터 없으면) 예외 발행
                .doOnSuccess(store -> System.out.println("--- StoreService: 가게 찾음 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: findStoreById 오류 - " + e.getMessage() + " ---"));
    }

    // 새로운 가게 생성 (요청 사용자 ID를 ownerId로 설정)
    // userId는 Controller에서 X-User-Id 헤더에서 가져와 전달합니다.
    public Mono<Store> createStore(Store store, String userId) { // userId 인자 추가
        System.out.println("--- StoreService: createStore 호출 (Name: " + store.getName() + ", UserID: " + userId + ", R2DBC) ---");
        // 1. 유효성 검증 (예: 필수 필드 누락 등) - 컨트롤러나 서비스에서 수행
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new SecurityException("User ID is required to create a store.")); // 사용자 ID 없으면 오류 (403/401 매핑)
        }
        if (store.getName() == null || store.getName().trim().isEmpty() /* || 다른 필수 필드 검증 */) {
            return Mono.error(new IllegalArgumentException("Store name is required.")); // 필수 필드 누락 (400 매핑)
        }


        // 2. 요청 사용자 ID를 가게의 ownerId로 설정
        store.setOwnerId(userId); // <-- ownerId 설정
        store.setCreatedAt(LocalDateTime.now()); // 생성 시간 설정 (Auditing이 자동 처리하지 않는다면 수동 설정)
        store.setUpdatedAt(LocalDateTime.now()); // 수정 시간 설정 (생성 시에도 동일)


        // R2DBC는 ID 자동 생성 후 save 메서드의 반환 객체에 ID를 채워줌
        return storeRepository.save(store) // R2dbcRepository의 save()는 Mono<Store>를 반환
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 생성 완료 (ID: " + savedStore.getId() + ", OwnerID: " + savedStore.getOwnerId() + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: createStore 오류 - " + e.getMessage() + " ---"));
        // 서비스 메서드는 발생한 예외를 Controller로 다시 발행하는 것이 일반적입니다.
    }

    // 특정 가게 정보 수정 (가게 주인만 수정 가능)
    // 순서: 찾기 -> 권한 확인 -> 업데이트 -> 저장
    // userId는 Controller에서 X-User-Id 헤더에서 가져와 전달합니다.
    public Mono<Store> updateStore(Long id, Store updatedStore, String userId) { // userId 인자 추가
        System.out.println("--- StoreService: updateStore 호출 (ID: " + id + ", UserID: " + userId + ", R2DBC) ---");
        // 1. 먼저 기존 가게를 ID로 찾습니다.
        return storeRepository.findById(id) // Mono<Store> 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id))) // 없으면 예외 발행 (404 매핑)
                .flatMap(existingStore -> { // 가게를 찾으면 다음 단계 실행 (Mono<Store> -> Mono<Publisher<R>>)
                    System.out.println("--- StoreService: 수정할 가게 찾음 (ID: " + id + ", OwnerID: " + existingStore.getOwnerId() + ") ---");

                    // 2. 권한 확인: 기존 가게의 ownerId와 요청 사용자 ID 비교
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        // 사용자 ID가 null이거나 ownerId와 다르면 권한 없음 예외 발행 (403 매핑)
                        System.err.println("--- StoreService: 권한 없음 - 사용자 ID " + userId + "는 가게 " + id + "의 주인이 아닙니다. ---");
                        return Mono.error(new SecurityException("User is not authorized to update this store."));
                    }
                    System.out.println("--- StoreService: 권한 확인 통과 --- 업데이트 시작 ---");

                    // 3. 기존 가게 엔티티의 필드들을 업데이트할 데이터로 채웁니다.
                    // 클라이언트에서 받은 updatedStore 객체에서 변경 가능한 필드만 복사합니다.
                    // ID, ownerId, createdAt 등은 변경하지 않도록 합니다.
                    existingStore.setName(updatedStore.getName());
                    existingStore.setDescription(updatedStore.getDescription());
                    existingStore.setAddress(updatedStore.getAddress());
                    existingStore.setLatitude(updatedStore.getLatitude());
                    existingStore.setLongitude(updatedStore.getLongitude());
                    existingStore.setPhone(updatedStore.getPhone());
                    existingStore.setOpeningHours(updatedStore.getOpeningHours());
                    existingStore.setStatus(updatedStore.getStatus()); // Enum 타입 복사
                    existingStore.setCategory(updatedStore.getCategory()); // Enum 타입 복사
                    // Note: R2DBC Auditing 설정이 되어 있다면 updatedAt은 자동으로 처리됩니다. createdAt은 변경 안됨.
                    existingStore.setUpdatedAt(LocalDateTime.now()); // Auditing이 자동 처리하지 않는다면 수동 설정


                    // 4. 업데이트된 엔티티를 저장합니다.
                    // save는 저장 후 업데이트된 Mono<Store> 반환
                    return storeRepository.save(existingStore);
                })
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 수정 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: updateStore 오류 - " + e.getMessage() + " ---"));
        // 서비스 메서드는 발생한 예외를 Controller로 다시 발행
    }

    // 특정 가게 삭제 (가게 주인만 삭제 가능)
    // 순서: 존재 확인 -> 권한 확인 -> 삭제
    // userId는 Controller에서 X-User-Id 헤더에서 가져와 전달합니다.
    public Mono<Void> deleteStore(Long id, String userId) { // userId 인자 추가
        System.out.println("--- StoreService: deleteStore 호출 (ID: " + id + ", UserID: " + userId + ", R2DBC) ---");
        // 1. 삭제할 가게를 먼저 찾습니다. (존재 확인 및 ownerId 파악)
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id))) // 없으면 예외 발행 (404 매핑)
                .flatMap(existingStore -> { // 가게를 찾으면 다음 단계 실행
                    System.out.println("--- StoreService: 삭제할 가게 찾음 (ID: " + id + ", OwnerID: " + existingStore.getOwnerId() + ") ---");

                    // 2. 권한 확인: 기존 가게의 ownerId와 요청 사용자 ID 비교
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        // 사용자 ID가 null이거나 ownerId와 다르면 권한 없음 예외 발행 (403 매핑)
                        System.err.println("--- StoreService: 권한 없음 - 사용자 ID " + userId + "는 가게 " + id + "의 주인이 아닙니다. ---");
                        return Mono.error(new SecurityException("User is not authorized to delete this store."));
                    }
                    System.out.println("--- StoreService: 권한 확인 통과 --- 삭제 시작 ---");

                    // 3. 권한이 확인되면 deleteById 호출
                    // deleteById는 Mono<Void>를 반환하며 존재하지 않아도 오류 나지 않지만,
                    // 우리는 이미 findById로 존재 여부를 확인했으므로 안전합니다.
                    return storeRepository.deleteById(id)
                            .doOnSuccess(aVoid -> System.out.println("--- StoreService: 가게 삭제 완료 (ID: " + id + ") ---"))
                            .doOnError(e -> System.err.println("--- StoreService: deleteStore 오류 중 삭제 실패 - " + e.getMessage() + " ---"));
                })
                .onErrorResume(e -> {
                    // findById 또는 flatMap 체인 중 발생한 오류를 잡습니다.
                    System.err.println("--- StoreService: deleteStore 오류 - " + e.getMessage() + " ---");
                    return Mono.error(e); // 오류를 컨트롤러로 다시 전달합니다.
                });
    }

    // 가게 이름으로 검색 (권한 확인 필요 없음)
    // findByNameContainingIgnoreCase 메서드가 StoreRepository에 정의되어 있어야 합니다.
    // public interface StoreRepository extends R2dbcRepository<Store, Long> { ... Flux<Store> findByNameContainingIgnoreCase(String name); ... }
    public Flux<Store> searchStoresByName(String name) {
        System.out.println("--- StoreService: searchStoresByName 호출 (검색어: " + name + ", R2DBC) ---");
        // R2dbcRepository의 쿼리 메서드 호출
        return storeRepository.findByNameContainingIgnoreCase(name) // <-- 이 메서드가 StoreRepository에 정의되어 있다고 가정
                .doOnComplete(() -> System.out.println("--- StoreService: searchStoresByName 완료 ---"))
                .doOnError(e -> System.err.println("--- StoreService: searchStoresByName 오류 - " + e.getMessage() + " ---"));
    }


}
