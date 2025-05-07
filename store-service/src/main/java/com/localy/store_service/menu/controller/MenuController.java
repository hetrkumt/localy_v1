package com.localy.store_service.menu.controller;

import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.service.MenuService;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.NoSuchElementException;

@RestController // 이 클래스가 REST 컨트롤러임을 나타냅니다.
@RequestMapping("/api/menus") // 이 컨트롤러의 기본 경로 설정
@RequiredArgsConstructor
public class MenuController {

    // 메뉴 서비스 의존성 주입
    private final MenuService menuService;

    // --- 메뉴 서비스 API 엔드포인트 ---

    // GET /api/menus/{menuId} 요청 처리: ID로 특정 메뉴 항목 조회
    @GetMapping("/{menuId}")
    public Mono<ResponseEntity<Menu>> getMenuById(@PathVariable Long menuId) {
        System.out.println("--- MenuController: GET /api/menus/" + menuId + " 요청 수신 ---");
        return menuService.findMenuById(menuId) // 서비스 호출 (Mono<Menu> 반환)
                .map(ResponseEntity::ok) // Mono가 데이터를 발행하면 (메뉴를 찾으면) 200 OK 응답 생성
                .onErrorResume(NoSuchElementException.class, e -> {
                    // 서비스에서 NoSuchElementException 발생 시, 404 Not Found 응답 생성
                    System.err.println("--- MenuController: 메뉴 조회 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build()); // ResponseEntity<Void> 반환
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시, 500 Internal Server Error 응답 생성
                    System.err.println("--- MenuController: 메뉴 조회 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    // 응답 본문 없이 (ResponseEntity<Void>) 500 반환
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)); // ResponseEntity<Void> 반환
                });
    }

    // GET /api/menus/stores/{storeId}/menus 요청 처리: 특정 가게 ID로 모든 메뉴 항목 조회
    // 참고: 이 엔드포인트는 논리적으로 /api/stores/{storeId}/menus 에 더 적합할 수도 있으나,
    // 메뉴 서비스에서 메뉴 데이터를 가져오므로 여기에 구현합니다.
    @GetMapping("/stores/{storeId}/menus")
    public Flux<Menu> getMenusByStoreId(@PathVariable Long storeId) {
        System.out.println("--- MenuController: GET /api/menus/stores/" + storeId + "/menus 요청 수신 ---");
        // 서비스 호출 (Flux<Menu> 반환)
        return menuService.findMenusByStoreId(storeId)
                .doOnError(e -> System.err.println("--- MenuController: 특정 가게 메뉴 조회 오류 - " + e.getMessage() + " ---"));
        // 특정 가게에 메뉴가 없을 경우 빈 Flux가 반환되며, 클라이언트에게는 빈 JSON 배열 [] 로 응답됩니다 (검색/목록 조회 시 표준).
        // 이 경우 404를 반환할 필요는 일반적으로 없습니다.
    }


    // POST /api/menus 요청 처리: 새로운 메뉴 항목 생성
// MenuController.java (createMenu 메서드 수정 예시)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Menu>> createMenu(
            @RequestPart("menu") Mono<Menu> menuJson,
            @RequestPart(value = "image", required = false) Mono<FilePart> imageFileMono,
            ServerWebExchange exchange
    ) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new SecurityException("User ID header missing. Authentication required via Edge."));
        }

        return Mono.zip(
                        menuJson,
                        imageFileMono.defaultIfEmpty(null)  // null 체크 제거
                )
                .flatMap(tuple -> {
                    Menu menu = tuple.getT1();
                    FilePart filePart = tuple.getT2();

                    if (menu.getStoreId() == null) {
                        return Mono.error(new IllegalArgumentException("Store ID is required for a menu."));
                    }

                    return menuService.createMenuWithImage(menu, filePart, userId);
                })
                .map(savedMenu ->
                        ResponseEntity.status(HttpStatus.CREATED).body(savedMenu)
                )
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build())
                )
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())  // 401로 통일
                )
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
                );
    }


    @PutMapping(
            path = "/{menuId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<Object>> updateMenu(
            @PathVariable Long menuId,
            @RequestPart("menu") Mono<Menu> menuJson,
            @RequestPart(value = "image", required = false) Mono<FilePart> imageFileMono,
            ServerWebExchange exchange
    ) {
        String currentUserId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return Mono.error(new SecurityException("User ID header missing. Authentication required via Edge."));
        }

        return Mono.zip(
                        menuJson,
                        imageFileMono.defaultIfEmpty(null)  // null 체크 제거
                )
                .flatMap(tuple -> {
                    Menu updatedMenu = tuple.getT1();
                    FilePart imageFile = tuple.getT2();

                    // 경로 변수와 본문 ID 일치 검증
                    if (updatedMenu.getId() != null && !updatedMenu.getId().equals(menuId)) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    return menuService.updateMenuWithImage(menuId, updatedMenu, imageFile, currentUserId);
                })
                .flatMap(savedMenu ->
                        Mono.just(ResponseEntity.ok(savedMenu))
                )
                .onErrorResume(NoSuchElementException.class, e ->
                        Mono.just(ResponseEntity.notFound().build())
                )
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())  // 401로 변경
                )
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build())
                )
                .onErrorResume(e -> {
                    System.err.println("--- MenuController: 메뉴 수정 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // DELETE /api/menus/{menuId} 요청 처리: 메뉴 항목 삭제
    // 삭제 성공 시 일반적으로 204 No Content (본문 없음) 반환
    @DeleteMapping("/{menuId}")
    public Mono<ResponseEntity<Object>> deleteMenu(@PathVariable Long menuId) {
        System.out.println("--- MenuController: DELETE /api/menus/" + menuId + " 요청 수신 ---");
        // 서비스 호출 (Mono<Void> 반환)
        return menuService.deleteMenu(menuId)
                .then(Mono.just(ResponseEntity.noContent().build())) // deleteMenu Mono<Void>가 완료되면 (삭제 성공 시) 204 No Content 응답 생성
                .onErrorResume(NoSuchElementException.class, e -> {
                    // 서비스에서 NoSuchElementException 발생 시 (메뉴를 찾을 수 없으면), 404 Not Found 반환
                    System.err.println("--- MenuController: 메뉴 삭제 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build()); // ResponseEntity<Void> 반환
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시 500 Internal Server Error 반환
                    System.err.println("--- MenuController: 메뉴 삭제 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    // 응답 본문 없이 (ResponseEntity<Void>) 500 반환
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)); // ResponseEntity<Void> 반환
                });
    }

    // --- 필요에 따라 다른 메뉴 관련 엔드포인트 추가 ---
    // 예: 모든 메뉴 목록 조회 (이 엔드포인트는 실제로 많이 사용되지는 않을 수 있습니다.)
    // @GetMapping
    // public Flux<Menu> getAllMenus() {
    //     System.out.println("--- MenuController: GET /api/menus 요청 수신 ---");
    //     return menuService.findAllMenus() // MenuService에 findAllMenus() 메서드를 추가해야 합니다.
    //             .doOnError(e -> System.err.println("--- MenuController: 전체 메뉴 조회 오류 - " + e.getMessage() + " ---"));
    // }
}