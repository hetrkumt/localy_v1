package com.localy.store_service.review.controller;

import com.localy.store_service.review.domain.Review;
import com.localy.store_service.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;
import java.util.Arrays; // 스택 트레이스 로깅용

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    private Mono<String> getUserIdFromHeaders(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new SecurityException("Authentication required: X-User-Id header missing."));
        }
        return Mono.just(userId);
    }

    @GetMapping("/{reviewId}")
    public Mono<ResponseEntity<Review>> getReviewById(@PathVariable Long reviewId) {
        return reviewService.findReviewById(reviewId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> handleControllerError(e, "리뷰 조회", false));
    }

    @GetMapping("/stores/{storeId}/reviews")
    public Flux<Review> getReviewsByStoreId(@PathVariable Long storeId) {
        return reviewService.findReviewsByStoreId(storeId)
                .doOnError(e -> System.err.println("--- ReviewController: 특정 가게 리뷰 조회 오류 - " + e.getMessage() + " ---"));
    }

    @GetMapping("/users/{userId}/reviews")
    public Flux<Review> getReviewsByUserId(@PathVariable String userId) {
        return reviewService.findReviewsByUserId(userId)
                .doOnError(e -> System.err.println("--- ReviewController: 특정 사용자 리뷰 조회 오류 - " + e.getMessage() + " ---"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Review>> submitReview(
            @RequestPart("review") Mono<Review> reviewMono, // 리뷰 정보 JSON
            @RequestPart(value = "image", required = false) Mono<FilePart> imageFileMono, // 리뷰 이미지 파일 (선택)
            ServerWebExchange exchange) {
        System.out.println("--- ReviewController: POST /api/reviews (Multipart) 요청 수신 ---");
        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> reviewMono
                        .flatMap(review -> {
                            review.setUserId(userId); // 서비스에서 설정하기보다 컨트롤러에서 명확히 설정
                            if (review.getStoreId() == null) { // storeId는 필수
                                return Mono.error(new IllegalArgumentException("Store ID is required for a review."));
                            }
                            return reviewService.submitReview(review, imageFileMono);
                        }))
                .map(savedReview -> ResponseEntity.status(HttpStatus.CREATED).body(savedReview))
                .onErrorResume(e -> handleControllerError(e, "리뷰 생성", false));
    }

    @PutMapping(value = "/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Review>> updateReview(
            @PathVariable Long reviewId,
            @RequestPart("review") Mono<Review> reviewMono, // 수정할 리뷰 정보 JSON
            @RequestPart(value = "image", required = false) Mono<FilePart> newImageFileMono, // 새 리뷰 이미지 파일 (선택)
            ServerWebExchange exchange) {
        System.out.println("--- ReviewController: PUT /api/reviews/" + reviewId + " (Multipart) 요청 수신 ---");
        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> reviewMono
                        .flatMap(reviewData -> reviewService.updateReview(reviewId, reviewData, userId, newImageFileMono)))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> handleControllerError(e, "리뷰 수정", false));
    }

    @DeleteMapping("/{reviewId}")
    public Mono<ResponseEntity<Object>> deleteReview(@PathVariable Long reviewId, ServerWebExchange exchange) {
        System.out.println("--- ReviewController: DELETE /api/reviews/" + reviewId + " 요청 수신 ---");
        return getUserIdFromHeaders(exchange)
                .flatMap(userId -> reviewService.deleteReview(reviewId, userId))
                .then(Mono.just(ResponseEntity.noContent().<Object>build())) // 타입 명시
                .onErrorResume(e -> handleControllerError(e, "리뷰 삭제", true));
    }

    // 공통 오류 처리 헬퍼
    private <T> Mono<ResponseEntity<T>> handleControllerError(Throwable e, String action, boolean isDeleteOperation) {
        System.err.println("--- ReviewController: " + action + " 중 오류 발생 - " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
        // System.err.println("--- Stack Trace: " + Arrays.toString(e.getStackTrace()) + " ---"); // 필요시 스택 트레이스 로깅

        HttpStatus status;
        if (e instanceof NoSuchElementException) {
            status = HttpStatus.NOT_FOUND;
        } else if (e instanceof SecurityException) {
            status = HttpStatus.FORBIDDEN; // 또는 UNAUTHORIZED
        } else if (e instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (isDeleteOperation && status == HttpStatus.NO_CONTENT) { // delete 성공 시 then에서 처리되므로 여기까지 오면 오류
            return Mono.just(ResponseEntity.status(status).build());
        }
        // body(null)을 사용하기 위해 제네릭 타입 T를 명시적으로 캐스팅하거나, build() 사용
        return Mono.just(ResponseEntity.status(status).<T>body(null));
    }
}
