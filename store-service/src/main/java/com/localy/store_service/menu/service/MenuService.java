package com.localy.store_service.menu.service;

import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.repository.MenuRepository;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
// @RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;

    @Value("${app.image.storage-location:./uploads/images}")
    private String storageLocation;

    @Value("${app.image.url-path:/images/}")
    private String imageUrlPath;

    public MenuService(MenuRepository menuRepository, StoreRepository storeRepository) {
        this.menuRepository = menuRepository;
        this.storeRepository = storeRepository;
    }

    @PostConstruct
    public void initStorageDirectory() {
        Path path = Paths.get(storageLocation);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                System.out.println("--- Local Image Storage: Created directory: " + storageLocation + " ---");
            } catch (IOException e) {
                System.err.println("--- Local Image Storage: Failed to create directory: " + storageLocation + " - " + e.getMessage() + " ---");
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
                .onErrorResume(IOException.class, e -> Mono.error(new RuntimeException("Failed to save image file", e)));
    }

    private Mono<Void> deleteImageFile(String imageUrlOrPath) {
        if (imageUrlOrPath == null || imageUrlOrPath.isEmpty() || !imageUrlOrPath.startsWith(imageUrlPath)) {
            return Mono.empty();
        }
        String relativePath = imageUrlOrPath.substring(imageUrlPath.length());
        Path filePath = Paths.get(storageLocation).resolve(relativePath);
        return Mono.fromRunnable(() -> {
                    try {
                        Files.deleteIfExists(filePath);
                    } catch (IOException e) {
                        System.err.println("Failed to delete image: " + filePath + " - " + e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Boolean> isUserAuthorizedToManageStore(String userId, Long storeId) {
        if (userId == null) {
            return Mono.just(false);
        }
        return storeRepository.findById(storeId)
                .map(store -> userId.equals(store.getOwnerId()))
                .defaultIfEmpty(false)
                .onErrorResume(e -> Mono.just(false));
    }

    public Mono<Menu> createMenu(Menu menu, @Nullable FilePart imageFile, String userId) {
        if (menu.getStoreId() == null) {
            return Mono.error(new IllegalArgumentException("Store ID is required for a new menu."));
        }
        return isUserAuthorizedToManageStore(userId, menu.getStoreId())
                .flatMap(isAuthorized -> {
                    if (!isAuthorized) {
                        return Mono.error(new SecurityException("User is not authorized to add a menu to this store."));
                    }
                    Mono<String> imageUrlMono = uploadImageToStorage(imageFile).defaultIfEmpty("");
                    return imageUrlMono.flatMap(imageUrl -> {
                        if (!imageUrl.isEmpty()) {
                            menu.setImageUrl(imageUrl);
                        }
                        menu.setCreatedAt(LocalDateTime.now());
                        menu.setUpdatedAt(LocalDateTime.now());
                        return menuRepository.save(menu);
                    });
                });
    }

    public Mono<Menu> updateMenu(Long menuId, Menu updatedMenu, @Nullable FilePart newImageFile, String userId) {
        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")))
                .flatMap(existingMenu -> isUserAuthorizedToManageStore(userId, existingMenu.getStoreId())
                        .flatMap(isAuthorized -> {
                            if (!isAuthorized) {
                                return Mono.error(new SecurityException("User is not authorized to update this menu."));
                            }
                            Mono<String> newImageUrlMono = Mono.just(Optional.ofNullable(newImageFile))
                                    .flatMap(optionalFilePart -> optionalFilePart.map(this::uploadImageToStorage).orElse(Mono.empty()))
                                    .defaultIfEmpty(existingMenu.getImageUrl() == null ? "" : existingMenu.getImageUrl());

                            return newImageUrlMono.flatMap(newImageUrl -> {
                                String oldImageUrl = existingMenu.getImageUrl();
                                boolean imageChanged = !newImageUrl.isEmpty() && (oldImageUrl == null || !oldImageUrl.equals(newImageUrl));

                                existingMenu.setName(updatedMenu.getName());
                                existingMenu.setDescription(updatedMenu.getDescription());
                                existingMenu.setPrice(updatedMenu.getPrice());
                                existingMenu.setAvailable(updatedMenu.isAvailable());
                                if (!newImageUrl.isEmpty()) {
                                    existingMenu.setImageUrl(newImageUrl);
                                } else if (newImageFile == null && updatedMenu.getImageUrl() == null) {
                                    existingMenu.setImageUrl(null);
                                }

                                existingMenu.setUpdatedAt(LocalDateTime.now());

                                Mono<Void> deleteOldImageMono = Mono.empty();
                                if (imageChanged && oldImageUrl != null && !oldImageUrl.isEmpty()) {
                                    deleteOldImageMono = deleteImageFile(oldImageUrl);
                                }
                                return deleteOldImageMono.then(menuRepository.save(existingMenu));
                            });
                        }));
    }

    public Mono<Void> deleteMenu(Long menuId, String userId) {
        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")))
                .flatMap(existingMenu -> isUserAuthorizedToManageStore(userId, existingMenu.getStoreId())
                        .flatMap(isAuthorized -> {
                            if (!isAuthorized) {
                                return Mono.error(new SecurityException("User is not authorized to delete this menu."));
                            }
                            Mono<Void> deleteImageMono = Mono.empty();
                            if (existingMenu.getImageUrl() != null) {
                                deleteImageMono = deleteImageFile(existingMenu.getImageUrl());
                            }
                            return menuRepository.deleteById(menuId).then(deleteImageMono);
                        }));
    }

    public Mono<Menu> findMenuById(Long menuId) {
        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")));
    }

    public Flux<Menu> findMenusByStoreId(Long storeId) { // 페이지네이션 미지원 버전
        return menuRepository.findByStoreId(storeId);
    }

    public Flux<Menu> searchMenusInStore(Long storeId, @Nullable String keyword, Pageable pageable) {
        System.out.println("--- MenuService: searchMenusInStore 호출 (StoreID: " + storeId + ", Keyword: " + keyword + ") ---");
        if (keyword == null || keyword.trim().isEmpty()) {
            // 키워드가 없으면 해당 가게의 모든 메뉴를 페이지네이션하여 반환
            // MenuRepository에 findByStoreId(Long storeId, Pageable pageable)이 필요합니다.
            // 임시로 findByStoreId(storeId) 후 수동 페이지네이션
            return menuRepository.findByStoreId(storeId)
                    .skip(pageable.getOffset())
                    .take(pageable.getPageSize());
        }
        return menuRepository.findByStoreIdAndNameContainingIgnoreCaseOrStoreIdAndDescriptionContainingIgnoreCase(
                storeId, keyword, storeId, keyword, pageable
        );
    }

    /**
     * 메뉴 이름 키워드를 포함하는 메뉴가 있는 가게들의 ID 목록을 반환합니다.
     * @param keyword 메뉴 이름 검색 키워드
     * @return Flux<Long> store_id 목록
     */
    public Flux<Long> getStoreIdsByMenuNameKeyword(String keyword) {
        System.out.println("--- MenuService: getStoreIdsByMenuNameKeyword 호출 (Keyword: " + keyword + ") ---");
        if (keyword == null || keyword.trim().isEmpty()) {
            return Flux.empty();
        }
        return menuRepository.findDistinctStoreIdsByMenuNameContainingIgnoreCase(keyword.trim());
    }
}
