package com.localy.app.review.controller; // 적절한 패키지 경로 사용


import com.localy.store_service.review.domain.Review;
import com.localy.store_service.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // HTTP 상태 코드 임포트
import org.springframework.http.ResponseEntity; // 응답 엔티티 임포트
import org.springframework.web.bind.annotation.*; // REST 컨트롤러 관련 어노테이션 임포트
import reactor.core.publisher.Flux; // Reactor Flux 임포트
import reactor.core.publisher.Mono; // Reactor Mono 임포트
import org.springframework.web.server.ServerWebExchange; // 요청 컨텍스트 (헤더 접근 등) 가져오기용 임포트

import java.util.NoSuchElementException; // 서비스에서 발생한 예외 처리용

@RestController // 이 클래스가 REST 컨트롤러임을 나타냅니다.
@RequestMapping("/api/reviews") // 이 컨트롤러의 기본 경로 설정
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // --- 리뷰 서비스 API 엔드포인트 ---
    // 사용자 ID는 엣지 서비스가 추가한 X-User-Id 헤더에서 가져옵니다.

    // GET /api/reviews/{reviewId} 요청 처리: ID로 특정 리뷰 조회
    // 이 기능은 사용자 ID가 직접 필요하지 않으므로 그대로 둡니다.
    @GetMapping("/{reviewId}")
    public Mono<ResponseEntity<Review>> getReviewById(@PathVariable Long reviewId) {
        System.out.println("--- ReviewController: GET /api/reviews/" + reviewId + " 요청 수신 ---");
        return reviewService.findReviewById(reviewId)
                .map(ResponseEntity::ok)
                .onErrorResume(NoSuchElementException.class, e -> {
                    System.err.println("--- ReviewController: 리뷰 조회 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    System.err.println("--- ReviewController: 리뷰 조회 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // GET /api/reviews/stores/{storeId}/reviews 요청 처리: 특정 가게 ID로 모든 리뷰 조회
    // 이 기능도 사용자 ID가 직접 필요하지 않으므로 그대로 둡니다.
    @GetMapping("/stores/{storeId}/reviews")
    public Flux<Review> getReviewsByStoreId(@PathVariable Long storeId) {
        System.out.println("--- ReviewController: GET /api/reviews/stores/" + storeId + "/reviews 요청 수신 ---");
        return reviewService.findReviewsByStoreId(storeId)
                .doOnError(e -> System.err.println("--- MenuController: 특정 가게 메뉴 조회 오류 - " + e.getMessage() + " ---")); // 로그 메시지 수정 필요 (메뉴 -> 리뷰)
        // .doOnError(e -> System.err.println("--- ReviewController: 특정 가게 리뷰 조회 오류 - " + e.getMessage() + " ---")); // 이렇게 수정
    }

    // GET /api/reviews/users/{userId}/reviews 요청 처리: 특정 사용자 ID로 모든 리뷰 조회
    // Note: 보안상 실제 서비스에서는 이 엔드포인트 노출을 신중하게 고려해야 합니다.
    // 여기서는 경로 변수로 받은 userId를 서비스로 전달합니다. 필요시 X-User-Id와 비교하는 로직 추가 가능.
    @GetMapping("/users/{userId}/reviews")
    public Flux<Review> getReviewsByUserId(@PathVariable String userId) {
        System.out.println("--- ReviewController: GET /api/reviews/users/" + userId + "/reviews 요청 수신 ---");
        // 경로 변수로 받은 userId를 서비스로 전달
        return reviewService.findReviewsByUserId(userId)
                .doOnError(e -> System.err.println("--- ReviewController: 특정 사용자 리뷰 조회 오류 - " + e.getMessage() + " ---"));
    }


    // POST /api/reviews 요청 처리: 새로운 리뷰 제출 (생성)
    // 요청 사용자 ID를 X-User-Id 헤더에서 가져옵니다.
    @PostMapping
    public Mono<ResponseEntity<Review>> submitReview(@RequestBody Review review, ServerWebExchange exchange) {
        System.out.println("--- ReviewController: POST /api/reviews 요청 수신 ---");

        // --- X-User-Id 헤더에서 사용자 ID 가져오기 ---
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        System.out.println("--- ReviewController: X-User-Id 헤더에서 가져온 사용자 ID: " + userId + " ---");

        // --- 사용자 ID 누락 또는 비어 있는지 확인 (엣지 서비스 인증 실패 가능성) ---
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("--- ReviewController: X-User-Id 헤더 누락 또는 비어 있음 (인증 실패 가능성) ---");
            // 이 경우 401 Unauthorized 또는 403 Forbidden 응답
            return Mono.error(new SecurityException("User ID header missing. Authentication required via Edge."));
        }
        // -------------------------------------------------------------

        // Review 객체에 사용자 ID 설정 (클라이언트는 userId 보내지 않음)
        review.setUserId(userId);

        // storeId 유효성 검증 (요청 본문에 storeId가 있는지 확인)
        if (review.getStoreId() == null) {
            System.err.println("--- ReviewController: Store ID 누락 ---");
            return Mono.error(new IllegalArgumentException("Store ID is required for a review.")); // 400 Bad Request 처리 대상
        }

        // 서비스 호출 (Mono<Review> 반환)
        return reviewService.submitReview(review)
                .map(savedReview -> ResponseEntity.status(HttpStatus.CREATED).body(savedReview)) // 생성 성공 시 201 Created
                .doOnError(e -> System.err.println("--- ReviewController: 리뷰 생성 오류 - " + e.getMessage() + " ---")) // 오류 로그
                .onErrorResume(e -> {
                    // 발생 가능한 예외 처리 (IllegalArgumentException, SecurityException 등)
                    System.err.println("--- ReviewController: 리뷰 생성 중 예상치 못한 오류 (Resume) - " + e.getMessage() + " ---");
                    if (e instanceof IllegalArgumentException) {
                        // 유효성 검증 오류는 400 Bad Request 반환
                        return Mono.just(ResponseEntity.badRequest().body(null));
                    }
                    if (e instanceof SecurityException) { // X-User-Id 누락 등 보안 오류
                        // 401 Unauthorized 또는 403 Forbidden 반환 (정책에 따라)
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null)); // 예시: 401 Unauthorized
                    }
                    // 그 외 다른 예외 발생 시 500 Internal Server Error
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // PUT /api/reviews/{reviewId} 요청 처리: 기존 리뷰 수정
    // 리뷰 작성자 본인만 수정 가능. 사용자 ID를 X-User-Id 헤더에서 가져옵니다.
    @PutMapping("/{reviewId}")
    public Mono<ResponseEntity<Review>> updateReview(@PathVariable Long reviewId, @RequestBody Review updatedReview, ServerWebExchange exchange) {
        System.out.println("--- ReviewController: PUT /api/reviews/" + reviewId + " 요청 수신 ---");

        // --- X-User-Id 헤더에서 사용자 ID 가져오기 ---
        String currentUserId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        System.out.println("--- ReviewController: X-User-Id 헤더에서 가져온 사용자 ID: " + currentUserId + " ---");

        // --- 사용자 ID 누락 또는 비어 있는지 확인 ---
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            System.err.println("--- ReviewController: X-User-Id 헤더 누락 또는 비어 있음 (인증 실패 가능성) ---");
            return Mono.error(new SecurityException("User ID header missing. Authentication required via Edge."));
        }
        // ---------------------------------------------

        // 서비스 호출 시 리뷰 ID, 업데이트 데이터, 그리고 X-User-Id 헤더에서 가져온 사용자 ID를 함께 전달
        return reviewService.updateReview(reviewId, updatedReview, currentUserId)
                .map(ResponseEntity::ok) // 수정 성공 시 200 OK
                .onErrorResume(NoSuchElementException.class, e -> {
                    // 리뷰를 찾을 수 없으면 404 Not Found
                    System.err.println("--- ReviewController: 리뷰 수정 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(SecurityException.class, e -> { // 서비스에서 권한 없음 또는 X-User-Id 누락 오류
                    System.err.println("--- ReviewController: 리뷰 수정 오류 (Forbidden/Unauthorized) - " + e.getMessage() + " ---");
                    // 403 Forbidden 또는 401 Unauthorized 반환
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 예시: 403 Forbidden
                })
                .onErrorResume(e -> {
                    // 그 외 다른 예외 발생 시 500 Internal Server Error
                    System.err.println("--- ReviewController: 리뷰 수정 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // DELETE /api/reviews/{reviewId} 요청 처리: 리뷰 삭제
    // 리뷰 작성자 본인만 삭제 가능. 사용자 ID를 X-User-Id 헤더에서 가져옵니다.
    @DeleteMapping("/{reviewId}")
    public Mono<ResponseEntity<Object>> deleteReview(@PathVariable Long reviewId, ServerWebExchange exchange) {
        System.out.println("--- ReviewController: DELETE /api/reviews/" + reviewId + " 요청 수신 ---");

        // --- X-User-Id 헤더에서 사용자 ID 가져오기 ---
        String currentUserId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        System.out.println("--- ReviewController: X-User-Id 헤더에서 가져온 사용자 ID: " + currentUserId + " ---");

        // --- 사용자 ID 누락 또는 비어 있는지 확인 ---
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            System.err.println("--- ReviewController: X-User-Id 헤더 누락 또는 비어 있음 (인증 실패 가능성) ---");
            return Mono.error(new SecurityException("User ID header missing. Authentication required via Edge."));
        }
        // ---------------------------------------------

        // 서비스 호출 시 리뷰 ID와 X-User-Id 헤더에서 가져온 사용자 ID를 함께 전달
        return reviewService.deleteReview(reviewId, currentUserId)
                .then(Mono.just(ResponseEntity.noContent().build())) // 삭제 성공 시 204 No Content
                .onErrorResume(NoSuchElementException.class, e -> {
                    System.err.println("--- ReviewController: 리뷰 삭제 오류 (NotFound) - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(SecurityException.class, e -> { // 서비스에서 권한 없음 또는 X-User-Id 누락 오류
                    System.err.println("--- ReviewController: 리뷰 삭제 오류 (Forbidden/Unauthorized) - " + e.getMessage() + " ---");
                    // 403 Forbidden 또는 401 Unauthorized 반환
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)); // 예시: 403 Forbidden
                })
                .onErrorResume(e -> {
                    System.err.println("--- ReviewController: 리뷰 삭제 중 예상치 못한 오류 - " + e.getMessage() + " ---");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
                });
    }

    // --- 필요에 따라 다른 리뷰 관련 엔드포인트 추가 ---
    // 예: 모든 리뷰 목록 조회
    // @GetMapping
    // public Flux<Review> getAllReviews() { ... }

    // 예: 특정 가게의 평균 평점 조회 (StoreService에 추가될 수도 있지만 여기에 구현 가능)
    // @GetMapping("/stores/{storeId}/average-rating")
    // public Mono<ResponseEntity<Double>> getAverageRatingByStoreId(@PathVariable Long storeId) { ... }
}