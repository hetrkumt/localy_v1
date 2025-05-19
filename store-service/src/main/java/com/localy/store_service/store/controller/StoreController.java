package com.localy.store_service.store.controller;

import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    private Mono<String> getUserIdFromHeaders(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new SecurityException("Authentication required: X-User-Id header missing."));
        }
        return Mono.just(userId);
    }

    @GetMapping
    public Flux<Store> getAllStores(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String menuKeyword, // 메뉴 이름으로 가게 검색하는 파라미터 추가
            @RequestParam(required = false) String sortBy, // 정렬 기준 (예: "name", "rating", "reviewCount")
            @RequestParam(required = false, defaultValue = "ASC") String sortDirection, // 정렬 방향
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        System.out.println("--- StoreController: GET /api/stores (Search/Filter/Page) 요청 수신 ---");
        System.out.println("--- Params: name=" + name + ", category=" + category + ", menuKeyword=" + menuKeyword +
                ", sortBy=" + sortBy + ", sortDirection=" + sortDirection + ", page=" + page + ", size=" + size + " ---");

        Sort sort = Sort.unsorted(); // 기본값: 정렬 없음
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Sort.Direction direction = sortDirection.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        if (menuKeyword != null && !menuKeyword.trim().isEmpty()) {
            System.out.println("--- StoreController: Searching stores by menu keyword: " + menuKeyword + " ---");
            return storeService.findStoresByMenuName(menuKeyword, pageable);
        } else {
            // 기존의 가게 이름 또는 카테고리 검색 로직
            return storeService.searchAndFilterStores(name, category, pageable);
        }
    }

    @GetMapping("/{storeId}")
    public Mono<ResponseEntity<Store>> getStoreById(@PathVariable Long storeId) {
        return storeService.findStoreById(storeId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> handleControllerError(e, "가게 조회 (ID)", false));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Store>> createStore(
            @RequestPart("store") Mono<Store> storeMono,
            @RequestPart(value = "mainImage", required = false) Mono<FilePart> mainImageFileMono,
            @RequestPart(value = "galleryImages", required = false) Flux<FilePart> galleryImageFilesFlux,
            ServerWebExchange exchange) {
        System.out.println("--- StoreController: POST /api/stores (Multipart) 요청 수신 ---");
        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> storeMono
                        .flatMap(store -> storeService.createStore(store, userId, mainImageFileMono, galleryImageFilesFlux)))
                .map(savedStore -> ResponseEntity.status(HttpStatus.CREATED).body(savedStore))
                .onErrorResume(e -> handleControllerError(e, "가게 생성", false));
    }

    @PutMapping(value = "/{storeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Store>> updateStore(
            @PathVariable Long storeId,
            @RequestPart("store") Mono<Store> storeMono,
            @RequestPart(value = "mainImage", required = false) Mono<FilePart> newMainImageFileMono,
            @RequestPart(value = "galleryImages", required = false) Flux<FilePart> newGalleryImageFilesFlux,
            @RequestPart(value = "galleryImagesToDelete", required = false) Mono<List<String>> galleryImagesToDeleteMono,
            ServerWebExchange exchange) {
        System.out.println("--- StoreController: PUT /api/stores/" + storeId + " (Multipart) 요청 수신 ---");

        Mono<List<String>> actualGalleryImagesToDeleteMono = galleryImagesToDeleteMono.defaultIfEmpty(List.of());

        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> Mono.zip(storeMono, actualGalleryImagesToDeleteMono)
                        .flatMap(tuple -> {
                            Store storeData = tuple.getT1();
                            List<String> galleryUrlsToDelete = tuple.getT2();
                            return storeService.updateStore(storeId, storeData, userId, newMainImageFileMono, newGalleryImageFilesFlux, galleryUrlsToDelete);
                        })
                )
                .map(ResponseEntity::ok)
                .onErrorResume(e -> handleControllerError(e, "가게 수정", false));
    }

    @DeleteMapping("/{storeId}")
    public Mono<ResponseEntity<Object>> deleteStore(@PathVariable Long storeId, ServerWebExchange exchange) {
        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> storeService.deleteStore(storeId, userId))
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> handleControllerError(e, "가게 삭제", true));
    }

    private <T> Mono<ResponseEntity<T>> handleControllerError(Throwable e, String action, boolean isDeleteOperation) {
        System.err.println("--- StoreController: " + action + " 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
        HttpStatus status;
        if (e instanceof NoSuchElementException) {
            status = HttpStatus.NOT_FOUND;
        } else if (e instanceof SecurityException) {
            status = HttpStatus.FORBIDDEN;
        } else if (e instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (isDeleteOperation) {
            return Mono.just(ResponseEntity.status(status).<T>build());
        }
        return Mono.just(ResponseEntity.status(status).<T>body(null));
    }
}
