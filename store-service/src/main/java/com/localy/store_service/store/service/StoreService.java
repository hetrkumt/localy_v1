// 파일 위치: com.localy.store_service.store.service.StoreService.java
package com.localy.store_service.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localy.store_service.menu.domain.Menu; // Menu 임포트
import com.localy.store_service.menu.service.MenuService; // MenuService 임포트
import com.localy.store_service.review.domain.Review; // Review 임포트
import com.localy.store_service.review.service.ReviewService;
import com.localy.store_service.store.domain.Store;
// import com.localy.store_service.store.domain.StoreCategory; // 필요시 사용
import com.localy.store_service.store.repository.StoreRepository;
import io.micrometer.common.lang.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final ObjectMapper objectMapper;
    private final ReviewService reviewService;
    private final MenuService menuService; // MenuService 의존성 추가

    @Value("${app.image.storage-location:./uploads/store_images}")
    private String storageLocation;

    @Value("${app.image.url-path:/store-images/}")
    private String imageUrlPath;

    @PostConstruct
    public void initStorageDirectory() {
        Path path = Paths.get(storageLocation);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                System.out.println("--- Store Image Storage: Created directory: " + storageLocation + " ---");
            } catch (IOException e) {
                System.err.println("--- Store Image Storage: Failed to create directory: " + storageLocation + " - " + e.getMessage() + " ---");
            }
        }
    }

    // 이미지 저장/삭제 헬퍼 메서드 (이전과 동일)
    private Mono<String> uploadImageToStorage(@Nullable FilePart imageFile) {
        if (imageFile == null || imageFile.filename().isEmpty()) {
            return Mono.empty();
        }
        String originalFilename = imageFile.filename();
        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            fileExtension = originalFilename.substring(dotIndex);
        }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(storageLocation).resolve(uniqueFilename);

        return DataBufferUtils.write(imageFile.content(), filePath, StandardOpenOption.CREATE)
                .then(Mono.just(imageUrlPath + uniqueFilename))
                .onErrorResume(IOException.class, e -> Mono.error(new RuntimeException("Failed to save store image file: " + originalFilename, e)));
    }

    private Flux<String> uploadImagesToStorage(Flux<FilePart> imageFiles) {
        if (imageFiles == null) {
            return Flux.empty();
        }
        return imageFiles.flatMap(this::uploadImageToStorage);
    }

    private Mono<Void> deleteImageFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || !imageUrl.startsWith(imageUrlPath)) {
            return Mono.empty();
        }
        String relativePath = imageUrl.substring(imageUrlPath.length());
        Path filePath = Paths.get(storageLocation).resolve(relativePath);
        return Mono.fromRunnable(() -> {
                    try {
                        Files.deleteIfExists(filePath);
                    } catch (IOException e) {
                        System.err.println("--- Store Image Storage: Failed to delete image file: " + filePath + " - " + e.getMessage() + " ---");
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> deleteImageFiles(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(imageUrls)
                .flatMap(this::deleteImageFile)
                .then();
    }

    // 가게 정보에 평균 평점, 리뷰 개수, 메뉴 목록, 리뷰 목록을 채우는 헬퍼 메서드
    private Mono<Store> enrichStoreDetails(Store store) {
        if (store == null || store.getId() == null) {
            return Mono.justOrEmpty(store);
        }
        Long storeId = store.getId();

        Mono<Double> averageRatingMono = reviewService.getAverageRatingByStoreId(storeId).defaultIfEmpty(0.0);
        Mono<Long> reviewCountMono = reviewService.findReviewsByStoreId(storeId).count().defaultIfEmpty(0L);
        Flux<Menu> menusFlux = menuService.findMenusByStoreId(storeId); // 페이지네이션 없는 전체 메뉴 목록
        Flux<Review> reviewsFlux = reviewService.findReviewsByStoreId(storeId); // 페이지네이션 없는 전체 리뷰 목록 (필요시 페이지네이션 적용)

        return Mono.zip(averageRatingMono, reviewCountMono, menusFlux.collectList(), reviewsFlux.collectList())
                .map(tuple -> {
                    store.setAverageRating(tuple.getT1());
                    store.setReviewCount(tuple.getT2().intValue());
                    store.setMenus(tuple.getT3()); // @Transient 필드에 메뉴 목록 설정
                    store.setReviews(tuple.getT4()); // @Transient 필드에 리뷰 목록 설정
                    System.out.println("--- StoreService: Enriched store (ID: " + storeId + ") with " + tuple.getT3().size() + " menus and " + tuple.getT4().size() + " reviews.");
                    return store;
                })
                .defaultIfEmpty(store); // 혹시라도 zip에서 empty가 발생하면 기존 store 반환
    }

    public Flux<Store> findAllStores() {
        System.out.println("--- StoreService: findAllStores 호출 (R2DBC) ---");
        // 목록 조회 시에는 메뉴/리뷰를 모두 가져오면 성능에 부담이 될 수 있음.
        // 여기서는 평균 평점과 리뷰 수만 포함하고, 메뉴/리뷰는 상세 조회 시에만 가져오도록 유지.
        // 만약 목록에서도 일부 메뉴/리뷰 정보가 필요하다면 별도 로직 추가.
        return storeRepository.findAll()
                .flatMap(this::enrichStoreWithReviewInfo); // 이전 버전의 enrich (평점/리뷰 수만)
    }

    // 특정 가게 상세 정보 조회 시 메뉴와 리뷰 목록 포함
    public Mono<Store> findStoreById(Long id) {
        System.out.println("--- StoreService: findStoreById 호출 (ID: " + id + ", R2DBC) ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(this::enrichStoreDetails); // 메뉴와 리뷰까지 모두 채우는 메서드 호출
    }

    // 가게 생성 (이전과 동일, 초기에는 메뉴/리뷰 없음)
    public Mono<Store> createStore(Store store, String userId, Mono<FilePart> mainImageFileMono, Flux<FilePart> galleryImageFilesFlux) {
        // ... (이전 createStore 코드와 동일하게 유지) ...
        System.out.println("--- StoreService: createStore 호출 (Name: " + store.getName() + ", UserID: " + userId + ") ---");
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.error(new SecurityException("User ID is required to create a store."));
        }
        if (store.getName() == null || store.getName().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Store name is required."));
        }

        store.setOwnerId(userId);
        store.setCreatedAt(LocalDateTime.now());
        store.setUpdatedAt(LocalDateTime.now());
        store.setAverageRating(0.0); // 초기값
        store.setReviewCount(0);    // 초기값
        // 생성 시점에는 menus와 reviews는 비어있거나 null로 시작

        Mono<String> mainImageUrlMonoResult = mainImageFileMono
                .flatMap(this::uploadImageToStorage)
                .doOnNext(store::setMainImageUrl)
                .defaultIfEmpty("");

        Mono<List<String>> galleryImageUrlsMonoResult = galleryImageFilesFlux
                .flatMap(this::uploadImageToStorage)
                .collectList()
                .defaultIfEmpty(new ArrayList<>());

        return Mono.zip(mainImageUrlMonoResult, galleryImageUrlsMonoResult)
                .flatMap(tuple -> {
                    List<String> galleryImageUrls = tuple.getT2();
                    store.setGalleryImageUrls(galleryImageUrls); // List<String>을 JSON 문자열로 변환하여 저장
                    return storeRepository.save(store);
                })
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 생성 완료 (ID: " + savedStore.getId() + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: createStore 오류 - " + e.getMessage() + " ---"));
    }

    // 가게 수정 (수정 후 반환 시 메뉴/리뷰 포함)
    public Mono<Store> updateStore(Long id, Store updatedStoreData, String userId, Mono<FilePart> newMainImageFileMono, Flux<FilePart> newGalleryImageFilesFlux, List<String> galleryImagesToDelete) {
        // ... (기존 updateStore의 이미지 처리 및 기본 정보 업데이트 로직은 유지) ...
        System.out.println("--- StoreService: updateStore 호출 (ID: " + id + ", UserID: " + userId + ") ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(existingStore -> {
                    // ... (권한 확인 및 기본 필드 업데이트 로직) ...
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        return Mono.error(new SecurityException("User is not authorized to update this store."));
                    }
                    existingStore.setName(updatedStoreData.getName());
                    existingStore.setDescription(updatedStoreData.getDescription());
                    existingStore.setAddress(updatedStoreData.getAddress());
                    existingStore.setLatitude(updatedStoreData.getLatitude());
                    existingStore.setLongitude(updatedStoreData.getLongitude());
                    existingStore.setPhone(updatedStoreData.getPhone());
                    existingStore.setOpeningHours(updatedStoreData.getOpeningHours());
                    existingStore.setStatus(updatedStoreData.getStatus());
                    existingStore.setCategory(updatedStoreData.getCategory());
                    existingStore.setUpdatedAt(LocalDateTime.now());

                    Mono<String> mainImageProcessingMono = newMainImageFileMono
                            .flatMap(this::uploadImageToStorage)
                            .flatMap(newMainUrl -> {
                                String oldMainUrl = existingStore.getMainImageUrl();
                                existingStore.setMainImageUrl(newMainUrl);
                                return (oldMainUrl != null && !oldMainUrl.equals(newMainUrl)) ? deleteImageFile(oldMainUrl).thenReturn(newMainUrl) : Mono.just(newMainUrl);
                            })
                            .defaultIfEmpty(existingStore.getMainImageUrl() == null ? "" : existingStore.getMainImageUrl());

                    Mono<List<String>> galleryProcessingMono = Mono.defer(() -> {
                        List<String> currentGalleryUrls = new ArrayList<>(existingStore.getGalleryImageUrls());
                        List<Mono<Void>> deleteMonos = new ArrayList<>();

                        if (galleryImagesToDelete != null && !galleryImagesToDelete.isEmpty()) {
                            galleryImagesToDelete.forEach(urlToDelete -> {
                                if (currentGalleryUrls.remove(urlToDelete)) {
                                    deleteMonos.add(deleteImageFile(urlToDelete));
                                }
                            });
                        }

                        return Flux.concat(deleteMonos).then(
                                newGalleryImageFilesFlux
                                        .flatMap(this::uploadImageToStorage)
                                        .collectList()
                                        .map(newUploadedUrls -> {
                                            currentGalleryUrls.addAll(newUploadedUrls);
                                            return currentGalleryUrls;
                                        })
                                        .defaultIfEmpty(currentGalleryUrls)
                        );
                    });

                    return Mono.zip(mainImageProcessingMono, galleryProcessingMono)
                            .flatMap(tuple -> {
                                String finalMainImageUrl = tuple.getT1();
                                List<String> finalGalleryImageUrls = tuple.getT2();
                                existingStore.setMainImageUrl(finalMainImageUrl.isEmpty() ? null : finalMainImageUrl);
                                existingStore.setGalleryImageUrls(finalGalleryImageUrls);
                                return storeRepository.save(existingStore);
                            });
                })
                .flatMap(this::enrichStoreDetails) // 업데이트 후 메뉴와 리뷰까지 모두 채워서 반환
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 수정 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: updateStore 오류 - " + e.getMessage() + " ---"));
    }

    // 가게 삭제 (이전과 동일)
    public Mono<Void> deleteStore(Long id, String userId) {
        // ... (이전 deleteStore 코드와 동일하게 유지) ...
        System.out.println("--- StoreService: deleteStore 호출 (ID: " + id + ", UserID: " + userId + ") ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(existingStore -> {
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        return Mono.error(new SecurityException("User is not authorized to delete this store."));
                    }
                    Mono<Void> deleteMainImageMono = Mono.empty();
                    if (existingStore.getMainImageUrl() != null) {
                        deleteMainImageMono = deleteImageFile(existingStore.getMainImageUrl());
                    }
                    Mono<Void> deleteGalleryImagesMono = Mono.empty();
                    List<String> galleryUrls = existingStore.getGalleryImageUrls();
                    if (galleryUrls != null && !galleryUrls.isEmpty()) {
                        deleteGalleryImagesMono = deleteImageFiles(galleryUrls);
                    }
                    return Mono.when(deleteMainImageMono, deleteGalleryImagesMono)
                            .then(storeRepository.deleteById(id)); // 메뉴, 리뷰는 DB의 ON DELETE CASCADE에 의해 처리됨
                })
                .doOnSuccess(aVoid -> System.out.println("--- StoreService: 가게 삭제 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: deleteStore 오류 - " + e.getMessage() + " ---"));
    }

    // 가게 검색 및 필터링 (목록 조회 시에는 메뉴/리뷰 전체를 가져오지 않음, 필요시 enrichStoreWithReviewInfo 사용)
    public Flux<Store> searchAndFilterStores(
            @Nullable String name,
            @Nullable String categoryString,
            Pageable pageable) {
        // ... (이전 searchAndFilterStores 코드와 유사하게 유지하되, enrichStoreWithReviewInfo 사용) ...
        System.out.println("--- StoreService: searchAndFilterStores 호출 (Name: " + name + ", Category: " + categoryString + ") ---");
        Flux<Store> storesFlux;
        // ... (이전 검색/필터링 로직) ...
        if (name != null && !name.isEmpty() && categoryString != null && !categoryString.isEmpty()) {
            storesFlux = storeRepository.findByNameContainingIgnoreCase(name, pageable) // 임시로 이름으로만 검색 후 필터링
                    .filter(store -> categoryString.equalsIgnoreCase(store.getCategory().name()));
        } else if (name != null && !name.isEmpty()) {
            storesFlux = storeRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (categoryString != null && !categoryString.isEmpty()) {
            // StoreCategory enum 변환 및 Repository 호출 필요
            // storesFlux = storeRepository.findByCategory(StoreCategory.valueOf(categoryString.toUpperCase()), pageable);
            storesFlux = storeRepository.findAll(pageable.getSort()) // 임시
                    .filter(store -> categoryString.equalsIgnoreCase(store.getCategory().name()))
                    .skip(pageable.getOffset()).take(pageable.getPageSize());
        } else {
            storesFlux = storeRepository.findAll(pageable.getSort())
                    .skip(pageable.getOffset()).take(pageable.getPageSize());
        }
        return storesFlux.flatMap(this::enrichStoreWithReviewInfo); // 평균 평점 및 리뷰 수만 포함
    }

    // 메뉴 이름으로 가게 검색 (반환 시 메뉴/리뷰 포함)
    public Flux<Store> findStoresByMenuName(String menuNameKeyword, Pageable pageable) {
        System.out.println("--- StoreService: findStoresByMenuName 호출 (Keyword: " + menuNameKeyword + ") ---");
        if (menuNameKeyword == null || menuNameKeyword.trim().isEmpty()) {
            return Flux.empty();
        }
        return menuService.getStoreIdsByMenuNameKeyword(menuNameKeyword)
                .collectList()
                .flatMapMany(storeIds -> {
                    if (storeIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return storeRepository.findAllByIdIn(storeIds, pageable.getSort())
                            .skip(pageable.getOffset())
                            .take(pageable.getPageSize())
                            .flatMap(this::enrichStoreDetails); // 메뉴와 리뷰까지 모두 채워서 반환
                });
    }

    // 가게 정보에 평균 평점과 리뷰 개수만 채우는 헬퍼 메서드 (목록용)
    private Mono<Store> enrichStoreWithReviewInfo(Store store) {
        if (store == null || store.getId() == null) {
            return Mono.justOrEmpty(store);
        }
        Long storeId = store.getId();
        Mono<Double> averageRatingMono = reviewService.getAverageRatingByStoreId(storeId).defaultIfEmpty(0.0);
        Mono<Long> reviewCountMono = reviewService.findReviewsByStoreId(storeId).count().defaultIfEmpty(0L);

        return Mono.zip(averageRatingMono, reviewCountMono)
                .map(tuple -> {
                    store.setAverageRating(tuple.getT1());
                    store.setReviewCount(tuple.getT2().intValue());
                    // 여기서는 menus와 reviews를 설정하지 않음 (목록 조회 시 성능 고려)
                    return store;
                })
                .defaultIfEmpty(store);
    }
}
