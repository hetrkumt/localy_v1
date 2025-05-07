package com.localy.store_service.store.controller;

import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.service.StoreService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // HTTP 상태 코드 임포트
import org.springframework.http.ResponseEntity; // 응답 엔티티 임포트
import org.springframework.web.bind.annotation.*; // REST 컨트롤러 관련 어노테이션 임포트
import reactor.core.publisher.Flux; // Reactor Flux 임포트
import reactor.core.publisher.Mono; // Reactor Mono 임포트

import java.util.NoSuchElementException; // 서비스에서 발생한 예외 처리용

@RestController // 이 클래스가 REST 컨트롤러임을 나타냅니다.
@RequestMapping("/api/stores") // 이 컨트롤러의 기본 경로 설정
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService; // R2DBC 기반 StoreService 의존성 주입


    // GET /api/stores 요청 모든 가계 정보 조회 처리
    @GetMapping
    public Flux<Store> getAllStores() {
        System.out.println("--- StoreController: GET /api/stores 요청 수신 ---");
        return storeService.findAllStores(); // Service가 R2DBC에서 가져온 Flux<Store>를 반환
    }

    // GET /api/stores/{storeId} 요청 특정 가계 정보 조회 처리
    @GetMapping("/{storeId}")
    public Mono<ResponseEntity<Store>> getStoreById(@PathVariable Long storeId) {
        System.out.println("--- StoreController: GET /api/stores/" + storeId + " 요청 수신 ---");
        return storeService.findStoreById(storeId) // Service가 R2DBC에서 가져온 Mono<Store>를 반환
                .map(ResponseEntity::ok) // Mono<Store>가 데이터를 성공적으로 발행하면 200 OK 응답 생성
                .onErrorResume(NoSuchElementException.class, e -> {
                    // Service에서 NoSuchElementException 발생 시, 404 Not Found 응답 생성
                    System.err.println("--- StoreController: 가게 조회 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시, 500 Internal Server Error 응답 생성
                    System.err.println("--- StoreController: 가게 조회 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // POST /api/stores 요청 처리
    @PostMapping
    public Mono<ResponseEntity<Store>> createStore(@RequestBody Store store) {
        System.out.println("--- StoreController: POST /api/stores 요청 수신 (Store Name: " + store.getName() + ") ---");
        return storeService.createStore(store) // Service가 R2DBC save 후 Mono<Store>를 반환
                .map(savedStore -> ResponseEntity.status(HttpStatus.CREATED).body(savedStore)) // 저장 성공 시 201 Created 응답 반환
                .doOnError(e -> System.err.println("--- StoreController: 가게 생성 오류 - " + e.getMessage() + " ---")) // 오류 발생 시 로그 출력
                .onErrorResume(e -> {
                    // 생성 중 오류 발생 시 500 Internal Server Error 반환
                    System.err.println("--- StoreController: 가게 생성 오류 (Resume) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // PUT /api/stores/{storeId} 요청 처리
    @PutMapping("/{storeId}")
    public Mono<ResponseEntity<Store>> updateStore(@PathVariable Long storeId, @RequestBody Store updatedStore) {
        System.out.println("--- StoreController: PUT /api/stores/" + storeId + " 요청 수신 ---");
        return storeService.updateStore(storeId, updatedStore) // Service가 R2DBC save 후 Mono<Store>를 반환
                .map(ResponseEntity::ok) // 수정 성공 시 200 OK 응답 반환
                .onErrorResume(NoSuchElementException.class, e -> {
                    // Service에서 NoSuchElementException 발생 시 404 Not Found 반환
                    System.err.println("--- StoreController: 가게 수정 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시 500 Internal Server Error 반환
                    System.err.println("--- StoreController: 가게 수정 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // DELETE /api/stores/{storeId} 요청 처리
    @DeleteMapping("/{storeId}")
    public Mono<ResponseEntity<Object>> deleteStore(@PathVariable Long storeId) {
        System.out.println("--- StoreController: DELETE /api/stores/" + storeId + " 요청 수신 ---");
        return storeService.deleteStore(storeId) // Service가 deleteById 후 Mono<Void>를 반환
                .then(Mono.just(ResponseEntity.noContent().build())) // deleteStore Mono<Void>가 완료되면 204 No Content 응답 생성
                .onErrorResume(NoSuchElementException.class, e -> {
                    // Service에서 NoSuchElementException 발생 시 404 Not Found 반환
                    System.err.println("--- StoreController: 가게 삭제 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시 500 Internal Server Error 반환
                    System.err.println("--- StoreController: 가게 삭제 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // GET /api/stores/search?name={searchName} 요청 처리
    @GetMapping("/search")
    public Flux<Store> searchStoresByName(@RequestParam String name) {
        System.out.println("--- StoreController: GET /api/stores/search 요청 수신 (Name: " + name + ") ---");
        return storeService.searchStoresByName(name) // Service가 R2DBC에서 가져온 Flux<Store>를 반환
                .doOnError(e -> System.err.println("--- StoreController: 가게 검색 중 오류 - " + e.getMessage() + " ---"));
    }
}