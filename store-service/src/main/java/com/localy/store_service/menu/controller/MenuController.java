package com.localy.store_service.menu.controller;

// ... (기존 임포트) ...
import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.service.MenuService;

import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor; // Lombok RequiredArgsConstructor 임포트
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart; // FilePart 임포트
import org.springframework.web.bind.annotation.*; // Spring Web 어노테이션 임포트
import org.springframework.web.server.ServerWebExchange; // ServerWebExchange 임포트

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException; // 예외 임포트
import java.lang.IllegalArgumentException; // 예외 임포트
import java.lang.SecurityException; // 예외 임포트
import java.util.Arrays; // Arrays 임포트 (스택 트레이스 로깅용)


@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // --- 공통 헬퍼 메서드: X-User-Id 헤더 가져오기 ---
    private Mono<String> getUserIdFromHeaders(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        System.out.println("--- Controller: X-User-Id 헤더에서 가져온 사용자 ID: " + userId + " ---");
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- Controller: X-User-Id 헤더 누락 또는 비어 있음 ---");
            return Mono.error(new SecurityException("Authentication required: X-User-Id header missing."));
        }
        return Mono.just(userId);
    }


    // --- 메뉴 서비스 API 엔드포인트 ---

    // GET: ID로 메뉴 조회
    @GetMapping("/{menuId}")
    public Mono<ResponseEntity<Menu>> getMenuById(@PathVariable Long menuId) {
        System.out.println("--- MenuController: GET /api/menus/" + menuId + " 요청 수신 ---");
        return menuService.findMenuById(menuId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e -> {
                    System.err.println("--- MenuController: 메뉴 조회 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    System.err.println("--- MenuController: 메뉴 조회 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // GET: StoreId로 메뉴 목록 조회
    @GetMapping("/stores/{storeId}/menus")
    public Flux<Menu> getMenusByStoreId(@PathVariable Long storeId) {
        System.out.println("--- MenuController: GET /api/menus/stores/" + storeId + "/menus 요청 수신 ---");
        return menuService.findMenusByStoreId(storeId)
                .doOnError(e -> System.err.println("--- MenuController: 특정 가게 메뉴 조회 오류 - " + e.getMessage() + " ---"));
    }


    // POST: 새로운 메뉴 생성 (이미지 업로드 포함)
    // 클라이언트로부터 multipart/form-data 형태의 요청을 받습니다.
    // "menu" 파트 (메뉴 정보 JSON)와 "image" 파트 (이미지 파일, 선택 사항)를 기대합니다.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // 요청의 Content-Type 명시
    public Mono<ResponseEntity<Menu>> createMenu(
            @RequestPart("menu") Mono<Menu> menuJson, // 'menu' 라는 이름의 파트 (JSON 형태의 메뉴 데이터)
            @RequestPart(value = "image", required = false) Mono<FilePart> imageFileMono, // 'image' 라는 이름의 파트 (업로드 파일), 필수 아님
            ServerWebExchange exchange // 요청 컨텍스트
    ) {
        System.out.println("--- MenuController: POST /api/menus 요청 수신 (Multipart with Image) ---");

        // 1. X-User-Id 헤더에서 사용자 ID 가져오기 및 유효성 검증
        return getUserIdFromHeaders(exchange) // Mono<String> (userId) 반환 또는 오류 발생
                .flatMap(userId -> {
                    // 2. 필요한 입력 데이터 (menu JSON, optional image FilePart)를 Reactive 체인으로 결합
                    // imageFileMono가 null인 경우 Mono.empty()로 대체하는 방어 로직 포함 (redundant with zip handling null)
                    // Mono<FilePart> safeImageFileMono = (imageFileMono != null ? imageFileMono : Mono.empty());

                    // Zip the menuJson Mono with the imageFileMono.
                    // If imageFileMono emits null (because required=false and the part is missing),
                    // Mono.zip will emit a Tuple2 where the second element is null.
                    // If imageFileMono is Mono.empty() (less likely for @RequestPart(required=false)),
                    // Mono.zip will complete empty.
                    return Mono.zip(menuJson, imageFileMono) // <-- Modified: Zipping directly
                            .flatMap(tuple -> { // This flatMap lambda is lambda$createMenu$4 (line 102 in your stack trace)
                                // 3. 튜플에서 데이터 추출 및 서비스 메서드 호출
                                Menu menu = tuple.getT1(); // 'menu' 파트 (Menu 객체)
                                // imageFile can be null here if the 'image' part was missing in the request
                                FilePart imageFile = tuple.getT2();

                                System.out.println("--- MenuController: 서비스 createMenu 호출 ---");
                                // 서비스 호출: 메뉴 객체, FilePart (null 가능), 사용자 ID 전달
                                return menuService.createMenu(menu, imageFile, userId); // 서비스는 Mono<Menu> 반환 (권한 확인, 저장 포함)
                            })
                            // Add a switchIfEmpty here to handle the case where Mono.zip completes empty
                            // This happens if menuJson or imageFileMono completes empty.
                            // For @RequestPart, menuJson should not be empty if the part is present.
                            // imageFileMono might be empty if the part is present but has no content,
                            // or if @RequestPart(required=false) behaves unexpectedly as empty instead of null.
                            // Let's assume Mono.zip emitting null is the primary path for missing optional parts.
                            // If Mono.zip completes empty, it likely means a required part was missing or empty.
                            // We can return a Bad Request in that case.
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Missing required parts or empty request body."))); // Added switchIfEmpty for safety


                })
                // 4. 서비스 결과 처리 및 응답 생성
                .map(savedMenu -> {
                    System.out.println("--- MenuController: 메뉴 생성 체인 완료 --- 응답 201 Created 생성 ---");
                    return ResponseEntity.status(HttpStatus.CREATED).body(savedMenu); // 201 Created 응답
                })
                // 5. 서비스에서 발생한 예외 처리 및 HTTP 상태 코드 매핑
                .onErrorResume(e -> {
                    System.err.println("--- MenuController: 메뉴 생성 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
                    System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---");

                    if (e instanceof IllegalArgumentException) {
                        // 유효성 검증 실패 (예: Store ID 누락, 필수 필드 누락 등)
                        return Mono.just(ResponseEntity.badRequest().body(null)); // 400 Bad Request
                    }
                    if (e instanceof SecurityException) {
                        // 인증/인가 실패 (X-User-Id 누락, 권한 없음)
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 403 Forbidden (또는 401 Unauthorized)
                    }
                    if (e instanceof NoSuchElementException) {
                        // 서비스에서 storeId에 해당하는 가게를 찾을 수 없는 경우 등
                        return Mono.just(ResponseEntity.notFound().build()); // 404 Not Found
                    }
                    // 그 외 예상치 못한 모든 오류
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)); // 500 Internal Server Error
                });
    }

    // PUT: 메뉴 수정
    @PutMapping(
            path = "/{menuId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<Menu>> updateMenu(
            @PathVariable Long menuId,
            @RequestPart("menu") Mono<Menu> menuJson,
            @RequestPart(value = "image", required = false) Mono<FilePart> imageFileMono,
            ServerWebExchange exchange
    ) {
        System.out.println("--- MenuController: PUT /api/menus/" + menuId + " 요청 수신 (Multipart with Image) ---");

        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> {
                    // Zip the menuJson Mono with the optional imageFileMono.
                    // If imageFileMono emits null (because required=false and part is missing),
                    // Mono.zip will emit a Tuple2 where the second element is null.
                    return Mono.zip(menuJson, imageFileMono) // <-- Zipping directly
                            .flatMap(tuple -> {
                                Menu updatedMenu = tuple.getT1();
                                FilePart imageFile = tuple.getT2(); // Can be null

                                System.out.println("--- MenuController: 서비스 updateMenu 호출 (MenuID: " + menuId + ") ---");
                                return menuService.updateMenu(menuId, updatedMenu, imageFile, userId);
                            })
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Missing required parts or empty request body."))); // Added switchIfEmpty for safety
                })
                .map(savedMenu -> {
                    System.out.println("--- MenuController: 메뉴 수정 체인 완료 --- 응답 200 OK 생성 ---");
                    return ResponseEntity.ok(savedMenu);
                })
                .onErrorResume(e -> {
                    System.err.println("--- MenuController: 메뉴 수정 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
                    System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---");

                    if (e instanceof NoSuchElementException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof SecurityException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null));
                    }
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().body(null));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // DELETE: 메뉴 삭제
    @DeleteMapping("/{menuId}")
    public Mono<ResponseEntity<Object>> deleteMenu(@PathVariable Long menuId, ServerWebExchange exchange) {
        System.out.println("--- MenuController: DELETE /api/menus/" + menuId + " 요청 수신 ---");

        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> {
                    System.out.println("--- MenuController: 서비스 deleteMenu 호출 (MenuID: " + menuId + ") ---");
                    return menuService.deleteMenu(menuId, userId);
                })
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    System.err.println("--- MenuController: 메뉴 삭제 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
                    System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---");

                    if (e instanceof NoSuchElementException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    if (e instanceof SecurityException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }


    // GET: ID로 메뉴 조회
    public Mono<Menu> findMenuById(Long menuId) {
        System.out.println("--- MenuService: findMenuById 호출 (MenuID: " + menuId + ") ---");
        return null; // This method should be in MenuService, not Controller
    }

    // GET: StoreId로 메뉴 목록 조회
    public Flux<Menu> findMenusByStoreId(Long storeId) {
        System.out.println("--- MenuService: findMenusByStoreId 호출 (StoreID: " + storeId + ") ---");
        return null; // This method should be in MenuService, not Controller
    }

    // --- 필요에 따라 다른 메서드 추가 (예: 모든 메뉴 조회 등) ---
}
