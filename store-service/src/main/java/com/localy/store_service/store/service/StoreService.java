package com.localy.store_service.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localy.store_service.menu.service.MenuService; // MenuService 임포트
import com.localy.store_service.review.service.ReviewService;
import com.localy.store_service.store.domain.Store;
import com.localy.store_service.store.domain.StoreCategory; // StoreCategory 임포트 (가정)
import com.localy.store_service.store.repository.StoreRepository;
import io.micrometer.common.lang.Nullable;
import jakarta.annotation.PostConstruct;
// lombok.RequiredArgsConstructor; // 생성자 직접 작성
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
// @RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final ObjectMapper objectMapper;
    private final ReviewService reviewService;
    private final MenuService menuService; // MenuService 의존성 추가

    @Value("${app.image.storage-location:./uploads/store_images}")
    private String storageLocation;

    @Value("${app.image.url-path:/store-images/}")
    private String imageUrlPath;

    public StoreService(StoreRepository storeRepository, ObjectMapper objectMapper, ReviewService reviewService, MenuService menuService) {
        this.storeRepository = storeRepository;
        this.objectMapper = objectMapper;
        this.reviewService = reviewService;
        this.menuService = menuService; // MenuService 주입
    }

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

    private Mono<Store> enrichStoreWithReviewInfo(Store store) {
        if (store == null || store.getId() == null) {
            return Mono.justOrEmpty(store);
        }
        Mono<Double> averageRatingMono = reviewService.getAverageRatingByStoreId(store.getId()).defaultIfEmpty(0.0);
        Mono<Long> reviewCountMono = reviewService.findReviewsByStoreId(store.getId()).count().defaultIfEmpty(0L);

        return Mono.zip(averageRatingMono, reviewCountMono)
                .map(tuple -> {
                    store.setAverageRating(tuple.getT1());
                    store.setReviewCount(tuple.getT2().intValue());
                    return store;
                })
                .defaultIfEmpty(store);
    }

    public Flux<Store> findAllStores() { // 페이지네이션 미적용 버전 (기존 유지)
        System.out.println("--- StoreService: findAllStores 호출 (R2DBC) ---");
        return storeRepository.findAll()
                .flatMap(this::enrichStoreWithReviewInfo);
    }

    public Mono<Store> findStoreById(Long id) {
        System.out.println("--- StoreService: findStoreById 호출 (ID: " + id + ", R2DBC) ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(this::enrichStoreWithReviewInfo);
    }

    public Mono<Store> createStore(Store store, String userId, Mono<FilePart> mainImageFileMono, Flux<FilePart> galleryImageFilesFlux) {
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
        store.setAverageRating(0.0);
        store.setReviewCount(0);

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
                    store.setGalleryImageUrls(galleryImageUrls);
                    return storeRepository.save(store);
                })
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 생성 완료 (ID: " + savedStore.getId() + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: createStore 오류 - " + e.getMessage() + " ---"));
    }

    public Mono<Store> updateStore(Long id, Store updatedStoreData, String userId, Mono<FilePart> newMainImageFileMono, Flux<FilePart> newGalleryImageFilesFlux, List<String> galleryImagesToDelete) {
        // ... (이전 코드와 동일) ...
        System.out.println("--- StoreService: updateStore 호출 (ID: " + id + ", UserID: " + userId + ") ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(existingStore -> {
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        return Mono.error(new SecurityException("User is not authorized to update this store."));
                    }
                    System.out.println("--- StoreService: 권한 확인 통과 --- 업데이트 시작 (Store ID: " + id + ") ---");

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
                .flatMap(this::enrichStoreWithReviewInfo)
                .doOnSuccess(savedStore -> System.out.println("--- StoreService: 가게 수정 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: updateStore 오류 - " + e.getMessage() + " ---"));
    }

    public Mono<Void> deleteStore(Long id, String userId) {
        // ... (이전 코드와 동일) ...
        System.out.println("--- StoreService: deleteStore 호출 (ID: " + id + ", UserID: " + userId + ") ---");
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Store not found with ID: " + id)))
                .flatMap(existingStore -> {
                    if (userId == null || !userId.equals(existingStore.getOwnerId())) {
                        return Mono.error(new SecurityException("User is not authorized to delete this store."));
                    }
                    System.out.println("--- StoreService: 권한 확인 통과 --- 가게 삭제 시작 (Store ID: " + id + ") ---");

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
                            .then(storeRepository.deleteById(id));
                })
                .doOnSuccess(aVoid -> System.out.println("--- StoreService: 가게 삭제 완료 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- StoreService: deleteStore 오류 - " + e.getMessage() + " ---"));
    }

    public Flux<Store> searchAndFilterStores(
            @Nullable String name,
            @Nullable String categoryString, // StoreCategory Enum 대신 String으로 받음
            Pageable pageable) {
        System.out.println("--- StoreService: searchAndFilterStores 호출 (Name: " + name + ", Category: " + categoryString + ", Page: " + pageable.getPageNumber() + ", Size: " + pageable.getPageSize() + ") ---");

        Flux<Store> storesFlux;
        if (name != null && !name.isEmpty() && categoryString != null && !categoryString.isEmpty()) {
            // StoreCategory enum 변환 (StoreCategory Enum이 정의되어 있다고 가정)
            // StoreCategory categoryEnum = StoreCategory.fromString(categoryString.toUpperCase()); // 이 메서드가 StoreCategory Enum에 있어야 함
            // storesFlux = storeRepository.findByNameContainingIgnoreCaseAndCategory(name, categoryEnum, pageable);
            // 임시: 이름과 카테고리 둘 다 필터링하는 Repository 메서드가 없으므로, 이름으로만 검색 후 애플리케이션 레벨에서 필터링 (비효율적)
            // 또는 Repository에 해당 메서드 추가 필요
            storesFlux = storeRepository.findByNameContainingIgnoreCase(name, pageable)
                    .filter(store -> categoryString.equalsIgnoreCase(store.getCategory().name())); // StoreCategory Enum 가정
            System.out.println("--- StoreService: Searching by name AND category (filtering category in-memory) ---");
        } else if (name != null && !name.isEmpty()) {
            storesFlux = storeRepository.findByNameContainingIgnoreCase(name, pageable);
            System.out.println("--- StoreService: Searching by name only ---");
        } else if (categoryString != null && !categoryString.isEmpty()) {
            // StoreCategory categoryEnum = StoreCategory.fromString(categoryString.toUpperCase());
            // storesFlux = storeRepository.findByCategory(categoryEnum, pageable); // Repository에 이 메서드 필요
            // 임시: 카테고리 단독 검색 Repository 메서드 없다고 가정하고, findAll 후 필터링 (매우 비효율적)
            storesFlux = storeRepository.findAll(pageable.getSort()) // 정렬만 적용
                    .filter(store -> categoryString.equalsIgnoreCase(store.getCategory().name()))
                    .skip(pageable.getOffset()).take(pageable.getPageSize()); // 수동 페이지네이션
            System.out.println("--- StoreService: Searching by category only (filtering in-memory, inefficient) ---");
        } else {
            // storeRepository.findAllBy(pageable) 또는 findAll(Sort) 후 수동 페이지네이션
            storesFlux = storeRepository.findAll(pageable.getSort())
                    .skip(pageable.getOffset()).take(pageable.getPageSize());
            System.out.println("--- StoreService: Finding all with pagination and sort ---");
        }
        return storesFlux.flatMap(this::enrichStoreWithReviewInfo);
    }

    /**
     * 메뉴 이름 키워드를 포함하는 메뉴가 있는 가게들을 검색합니다. (페이지네이션 지원)
     * @param menuNameKeyword 메뉴 이름 검색 키워드
     * @param pageable 페이지 정보
     * @return 검색된 가게 목록 (Flux<Store>)
     */
    public Flux<Store> findStoresByMenuName(String menuNameKeyword, Pageable pageable) {
        System.out.println("--- StoreService: findStoresByMenuName 호출 (Keyword: " + menuNameKeyword + ") ---");
        if (menuNameKeyword == null || menuNameKeyword.trim().isEmpty()) {
            return Flux.empty(); // 키워드가 없으면 빈 결과 반환
        }
        return menuService.getStoreIdsByMenuNameKeyword(menuNameKeyword) // Flux<Long> (storeId 목록)
                .collectList() // Mono<List<Long>>
                .flatMapMany(storeIds -> {
                    if (storeIds.isEmpty()) {
                        return Flux.empty();
                    }
                    // findAllById는 Pageable을 직접 지원하지 않으므로, ID 목록으로 조회 후 수동 페이지네이션
                    // 또는 StoreRepository에 List<Long>과 Pageable을 받는 커스텀 메서드 필요
                    return storeRepository.findAllByIdIn(storeIds, pageable.getSort()) // findAllByIdIn(List<Long> ids, Sort sort)
                            .skip(pageable.getOffset())
                            .take(pageable.getPageSize())
                            .flatMap(this::enrichStoreWithReviewInfo);
                });
    }
}
