package com.localy.store_service.review.service;

import com.localy.store_service.review.domain.Review;
import com.localy.store_service.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;
// import java.time.LocalDateTime; // Auditing 사용 시 수동 설정 필요 없음

@Service // 이 클래스가 Service 컴포넌트임을 나타냅니다.
@RequiredArgsConstructor
public class ReviewService {

    // R2DBC Review Repository 의존성 주입
    private final ReviewRepository reviewRepository;

    // --- 리뷰 서비스 비즈니스 로직 (Reactive R2DBC 기반) ---

    // ID로 특정 리뷰 조회
    public Mono<Review> findReviewById(Long id) {
        System.out.println("--- ReviewService: findReviewById 호출 (ID: " + id + ") ---");
        return reviewRepository.findById(id) // Mono<Review> 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Review not found with ID: " + id))) // 없으면 예외 발행
                // .doOnSuccess(review -> System.out.println("--- ReviewService: 리뷰 찾음 (ID: " + id + ") ---")) // 상세 로그 제거
                .doOnError(e -> System.err.println("--- ReviewService: findReviewById 오류 - " + e.getMessage() + " ---")); // 오류 로그 유지
    }

    // 특정 가게 ID로 모든 리뷰 조회
    public Flux<Review> findReviewsByStoreId(Long storeId) {
        System.out.println("--- ReviewService: findReviewsByStoreId 호출 (StoreID: " + storeId + ") ---");
        return reviewRepository.findByStoreId(storeId) // Flux<Review> 반환
                // .doOnComplete(() -> System.out.println("--- ReviewService: findReviewsByStoreId 완료 ---")) // Flux 완료 로그 제거 (필요시 복원)
                .doOnError(e -> System.err.println("--- ReviewService: findReviewsByStoreId 오류 - " + e.getMessage() + " ---")); // 오류 로그 유지
    }

    // 특정 사용자 ID로 모든 리뷰 조회
    public Flux<Review> findReviewsByUserId(String userId) {
        System.out.println("--- ReviewService: findReviewsByUserId 호출 (UserID: " + userId + ") ---");
        return reviewRepository.findByUserId(userId) // Flux<Review> 반환
                // .doOnComplete(() -> System.out.println("--- ReviewService: findReviewsByUserId 완료 ---")) // Flux 완료 로그 제거 (필요시 복원)
                .doOnError(e -> System.err.println("--- ReviewService: findReviewsByUserId 오류 - " + e.getMessage() + " ---")); // 오류 로그 유지
    }


    // 새로운 리뷰 제출 (생성)
    // Review 객체에는 storeId, userId, rating, comment가 채워져 있다고 가정합니다.
    // createdAt/updatedAt은 @EnableR2dbcAuditing 설정 시 자동 처리됩니다.
    public Mono<Review> submitReview(Review review) {
        System.out.println("--- ReviewService: submitReview 호출 (StoreID: " + review.getStoreId() + ", UserID: " + review.getUserId() + ") ---");
        // Auditing 설정이 되어 있다면 createdAt/updatedAt은 save 시 자동 설정됩니다.
        // 수동 설정 코드는 제거합니다.
        // if (review.getCreatedAt() == null) { review.setCreatedAt(LocalDateTime.now()); }
        // if (review.getUpdatedAt() == null) { review.setUpdatedAt(LocalDateTime.now()); }

        return reviewRepository.save(review) // 저장 후 ID 채워진 Mono<Review> 반환
                .doOnSuccess(savedReview -> System.out.println("--- ReviewService: 리뷰 생성 완료 (ID: " + savedReview.getId() + ") ---")) // 성공 로그 유지
                .doOnError(e -> System.err.println("--- ReviewService: submitReview 오류 - " + e.getMessage() + " ---")); // 오류 로그 유지
    }

    // 기존 리뷰 수정
    // 흐름: 찾기 -> 권한 확인 -> 필드 업데이트 -> 저장
    // currentUserId는 요청을 보낸 인증된 사용자의 ID입니다. (Controller에서 전달받음)
    // updatedAt은 Auditing 설정 시 save에서 자동 업데이트됩니다.
    public Mono<Review> updateReview(Long id, Review updatedReview, String currentUserId) {
        System.out.println("--- ReviewService: updateReview 호출 (Review ID: " + id + ", UserID: " + currentUserId + ") ---");
        // 1. 수정하려는 리뷰를 ID로 찾습니다.
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Review not found with ID: " + id)))
                .flatMap(existingReview -> {
                    System.out.println("--- ReviewService: 수정할 리뷰 찾음 (ID: " + id + ") ---");
                    // 2. 요청한 사용자가 리뷰 작성자 본인인지 확인합니다.
                    if (!existingReview.getUserId().equals(currentUserId)) {
                        System.err.println("--- ReviewService: 수정 권한 없음 (Review ID: " + id + ", UserID: " + currentUserId + ") ---");
                        return Mono.error(new SecurityException("User is not authorized to update this review."));
                    }
                    System.out.println("--- ReviewService: 수정 권한 확인 완료 --- 업데이트 시작 ---");

                    // 3. 기존 리뷰의 수정 가능한 필드 (평점, 내용)를 업데이트할 데이터로 채웁니다.
                    // updatedReview에서 필요한 필드만 복사합니다. ID, storeId, userId 등은 수정 불가.
                    existingReview.setRating(updatedReview.getRating());
                    existingReview.setComment(updatedReview.getComment());
                    // updatedAt은 Auditing 설정 시 save에서 자동 업데이트됩니다.
                    // existingReview.setUpdatedAt(LocalDateTime.now()); // 수동 설정 제거

                    // 4. 업데이트된 엔티티를 저장합니다.
                    return reviewRepository.save(existingReview);
                })
                .doOnSuccess(savedReview -> System.out.println("--- ReviewService: 리뷰 수정 완료 (ID: " + id + ") ---")) // 성공 로그 유지
                .doOnError(e -> System.err.println("--- ReviewService: updateReview 오류 - " + e.getMessage() + " ---")); // 오류 로그 유지
    }

    // 리뷰 삭제
    // 흐름: 찾기 -> 권한 확인 -> 삭제
    // currentUserId는 요청을 보낸 인증된 사용자의 ID입니다. (Controller에서 전달받음)
    public Mono<Void> deleteReview(Long id, String currentUserId) {
        System.out.println("--- ReviewService: deleteReview 호출 (Review ID: " + id + ", UserID: " + currentUserId + ") ---");
        // 1. 삭제하려는 리뷰를 ID로 찾습니다. (권한 확인을 위해 필요)
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Review not found with ID: " + id)))
                .flatMap(existingReview -> {
                    System.out.println("--- ReviewService: 삭제할 리뷰 찾음 (ID: " + id + ") ---");
                    // 2. 요청한 사용자가 리뷰 작성자 본인인지 확인합니다.
                    if (!existingReview.getUserId().equals(currentUserId)) {
                        System.err.println("--- ReviewService: 삭제 권한 없음 (Review ID: " + id + ", UserID: " + currentUserId + ") ---");
                        return Mono.error(new SecurityException("User is not authorized to delete this review."));
                    }
                    System.out.println("--- ReviewService: 삭제 권한 확인 완료 --- 삭제 시작 ---");

                    // 3. 권한이 확인되면 deleteById 호출 (Mono<Void> 반환)
                    return reviewRepository.deleteById(id)
                            .doOnSuccess(aVoid -> System.out.println("--- ReviewService: 리뷰 삭제 완료 (ID: " + id + ") ---")) // 성공 로그 유지
                            .doOnError(e -> System.err.println("--- ReviewService: deleteReview 오류 중 삭제 실패 - " + e.getMessage() + " ---")); // 오류 로그 유지
                })
                .onErrorResume(e -> {
                    // findById, 권한 확인, deleteById 등 체인 중 발생한 오류를 잡습니다.
                    System.err.println("--- ReviewService: deleteReview 오류 - " + e.getMessage() + " ---"); // 오류 로그 유지
                    return Mono.error(e); // 오류를 다시 발행하여 컨트롤러로 전달
                });
    }

    // --- 추가적으로 필요한 비즈니스 로직 메서드 구현 ---
    // 예: 가게 평균 평점 계산
    // public Mono<Double> getAverageRatingByStoreId(Long storeId) {
    //     return reviewRepository.findByStoreId(storeId) // 특정 가게의 모든 리뷰 가져오기 (Flux<Review>)
    //             .map(Review::getRating) // 리뷰 객체를 평점(Integer)으로 변환 (Flux<Integer>)
    //             .collect(Collectors.averagingDouble(Integer::doubleValue)) // 평점들의 평균 계산 (Mono<Double>)
    //             // .defaultIfEmpty(0.0); // 리뷰가 없을 경우 기본값 0.0 반환 - 필요시 주석 해제
    // }
}
