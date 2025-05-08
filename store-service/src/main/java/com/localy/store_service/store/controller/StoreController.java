package com.localy.store_service.store.controller; // 적절한 컨트롤러 패키지 사용

// 필요한 임포트
import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.service.StoreService;
import lombok.RequiredArgsConstructor; // Lombok RequiredArgsConstructor 임포트
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange; // ServerWebExchange 임포트

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException; // 예외 임포트
import java.lang.SecurityException; // SecurityException 임포트
import java.lang.IllegalArgumentException; // IllegalArgumentException 임포트


@RestController // REST 컨트롤러임을 나타냅니다.
@RequestMapping("/api/stores") // 이 컨트롤러의 기본 경로 설정
@RequiredArgsConstructor // StoreService 의존성 주입
public class StoreController {

    private final StoreService storeService;

    // --- 헬퍼 메서드: X-User-Id 헤더 가져오기 (MenuController와 동일) ---
    // 여러 메서드에서 사용되므로 헬퍼 메서드로 분리
    private Mono<String> getUserIdFromHeaders(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        System.out.println("--- Controller: X-User-Id 헤더에서 가져온 사용자 ID: " + userId + " ---");
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- Controller: X-User-Id 헤더 누락 또는 비어 있음 ---");
            // 사용자 ID가 없으면 SecurityException 발행 (인증/인가 오류)
            return Mono.error(new SecurityException("Authentication required: X-User-Id header missing."));
        }
        return Mono.just(userId); // Mono<String>으로 감싸서 반환
    }

    // --- 가게 서비스 API 엔드포인트 ---

    // GET /api/stores 요청 모든 가계 정보 조회 처리 (권한 확인 필요 없음)
    @GetMapping
    public Flux<Store> getAllStores() {
        System.out.println("--- StoreController: GET /api/stores 요청 수신 ---");
        return storeService.findAllStores()
                .doOnError(e -> System.err.println("--- StoreController: 모든 가게 조회 오류 - " + e.getMessage() + " ---"));
    }

    // GET /api/stores/{storeId} 요청 특정 가계 정보 조회 처리 (권한 확인 필요 없음)
    @GetMapping("/{storeId}")
    public Mono<ResponseEntity<Store>> getStoreById(@PathVariable Long storeId) {
        System.out.println("--- StoreController: GET /api/stores/" + storeId + " 요청 수신 ---");
        return storeService.findStoreById(storeId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e -> {
                    System.err.println("--- StoreController: 가게 조회 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    System.err.println("--- StoreController: 가게 조회 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // POST /api/stores 요청 처리 (가게 생성 - 요청 사용자가 주인이 됨)
    @PostMapping
    public Mono<ResponseEntity<Store>> createStore(@RequestBody Store store, ServerWebExchange exchange) { // ServerWebExchange 인자 추가
        System.out.println("--- StoreController: POST /api/stores 요청 수신 (Store Name: " + store.getName() + ") ---");
        // 1. X-User-Id 헤더에서 사용자 ID 가져오기
        return getUserIdFromHeaders(exchange) // Mono<String> (userId) 반환 또는 오류 발생
                .flatMap(userId -> {
                    System.out.println("--- StoreController: 서비스 createStore 호출 (UserID: " + userId + ") ---");
                    // 2. 서비스 호출: 가게 정보와 사용자 ID 전달
                    return storeService.createStore(store, userId); // 서비스가 Mono<Store> 반환 (ownerId 설정 및 저장 포함)
                })
                // 3. 서비스 결과 처리 및 응답 생성
                .map(savedStore -> ResponseEntity.status(HttpStatus.CREATED).body(savedStore)) // 저장 성공 시 201 Created 응답
                // 4. 서비스에서 발생한 예외 처리 및 HTTP 상태 코드 매핑
                .onErrorResume(e -> {
                    System.err.println("--- StoreController: 가게 생성 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");

                    if (e instanceof IllegalArgumentException) {
                        // 서비스에서 유효성 검증 실패 (예: 필수 필드 누락)
                        return Mono.just(ResponseEntity.badRequest().body(null)); // 400 Bad Request
                    }
                    if (e instanceof SecurityException) {
                        // getUserIdFromHeaders 에서 발생 가능 (X-User-Id 누락 등)
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 403 Forbidden (또는 401 Unauthorized)
                    }
                    // 그 외 예상치 못한 모든 오류
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()); // 500 Internal Server Error
                });
    }

    // PUT /api/stores/{storeId} 요청 처리 (가게 주인만 수정 가능)
    @PutMapping("/{storeId}")
    public Mono<ResponseEntity<Store>> updateStore(@PathVariable Long storeId, @RequestBody Store updatedStore, ServerWebExchange exchange) { // ServerWebExchange 인자 추가
        System.out.println("--- StoreController: PUT /api/stores/" + storeId + " 요청 수신 ---");
        // 1. X-User-Id 헤더에서 사용자 ID 가져오기
        return getUserIdFromHeaders(exchange) // Mono<String> (userId) 반환 또는 오류 발생
                .flatMap(userId -> {
                    System.out.println("--- StoreController: 서비스 updateStore 호출 (ID: " + storeId + ", UserID: " + userId + ") ---");
                    // 2. 서비스 호출: 가게 ID, 업데이트 정보, 사용자 ID 전달
                    return storeService.updateStore(storeId, updatedStore, userId); // 서비스가 Mono<Store> 반환 (권한 확인 및 업데이트 포함)
                })
                // 3. 서비스 결과 처리 및 응답 생성
                .map(ResponseEntity::ok) // 수정 성공 시 200 OK 응답
                // 4. 서비스에서 발생한 예외 처리 및 HTTP 상태 코드 매핑
                .onErrorResume(e -> {
                    System.err.println("--- StoreController: 가게 수정 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");

                    if (e instanceof NoSuchElementException) {
                        // 서비스에서 가게를 찾을 수 없으면 404 Not Found
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof SecurityException) {
                        // 서비스에서 권한 없음 예외 발생
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 403 Forbidden
                    }
                    if (e instanceof IllegalArgumentException) {
                        // 서비스에서 유효성 검증 실패 (예: 필수 필드 누락)
                        return Mono.just(ResponseEntity.badRequest().body(null)); // 400 Bad Request
                    }
                    // 그 외 예상치 못한 모든 오류
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()); // 500 Internal Server Error
                });
    }

    // DELETE /api/stores/{storeId} 요청 처리 (가게 주인만 삭제 가능)
    @DeleteMapping("/{storeId}")
    public Mono<ResponseEntity<Object>> deleteStore(@PathVariable Long storeId, ServerWebExchange exchange) { // ServerWebExchange 인자 추가
        System.out.println("--- StoreController: DELETE /api/stores/" + storeId + " 요청 수신 ---");
        // 1. X-User-Id 헤더에서 사용자 ID 가져오기
        return getUserIdFromHeaders(exchange) // Mono<String> (userId) 반환 또는 오류 발생
                .flatMap(userId -> {
                    System.out.println("--- StoreController: 서비스 deleteStore 호출 (ID: " + storeId + ", UserID: " + userId + ") ---");
                    // 2. 서비스 호출: 가게 ID, 사용자 ID 전달
                    return storeService.deleteStore(storeId, userId); // 서비스가 Mono<Void> 반환 (권한 확인 및 삭제 포함)
                })
                // 3. 서비스 완료 후 응답 생성 (Mono<Void>가 완료되면)
                .then(Mono.just(ResponseEntity.noContent().build())) // 204 No Content 응답
                // 4. 서비스에서 발생한 예외 처리 및 HTTP 상태 코드 매핑
                .onErrorResume(e -> {
                    System.err.println("--- StoreController: 가게 삭제 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");

                    if (e instanceof NoSuchElementException) {
                        // 서비스에서 가게를 찾을 수 없으면 404 Not Found
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof SecurityException) {
                        // 서비스에서 권한 없음 예외 발생
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 403 Forbidden
                    }
                    // 그 외 예상치 못한 모든 오류
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()); // 500 Internal Server Error
                });
    }

    // GET /api/stores/search?name={searchName} 요청 처리 (권한 확인 필요 없음)
    @GetMapping("/search")
    public Flux<Store> searchStoresByName(@RequestParam String name) {
        System.out.println("--- StoreController: GET /api/stores/search 요청 수신 (Name: " + name + ") ---");
        return storeService.searchStoresByName(name)
                .doOnError(e -> System.err.println("--- StoreController: 가게 검색 중 오류 - " + e.getMessage() + " ---"));
    }

    // Note: Controller의 예외 처리에서 SecurityException은 403 Forbidden으로 매핑했습니다.
    // 필요에 따라 401 Unauthorized 등으로 변경할 수 있습니다.
    // IllegalArgumentException은 400 Bad Request로 매핑했습니다.
}
