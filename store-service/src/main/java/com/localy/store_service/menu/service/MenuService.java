package com.localy.store_service.menu.service; // 적절한 서비스 패키지 사용

// ... (필요한 모든 임포트) ...
import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.repository.MenuRepository; // MenuRepository 임포트
import com.localy.store_service.store.domain.Store; // Store 엔티티 임포트
import com.localy.store_service.store.repository.StoreRepository; // StoreRepository 임포트

import io.micrometer.common.lang.Nullable; // Nullable 임포트 확인
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service; // Service 어노테이션 임포트

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import reactor.core.scheduler.Schedulers; // Schedulers 임포트 (블록킹 I/O용)

import java.util.UUID;
import java.util.Optional;
import java.util.NoSuchElementException; // 예외 임포트
import java.lang.IllegalArgumentException; // 예외 임포트
import java.lang.SecurityException; // 예외 임포트
import java.time.LocalDateTime; // 시간 임포트
import reactor.core.publisher.SignalType; // SignalType 임포트
import java.math.BigDecimal; // BigDecimal 임포트


@Service // Spring 서비스 빈으로 등록
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository; // StoreRepository 의존성 추가

    @Value("${app.image.storage-location:./uploads/images}") // application.yml 경로 사용
    private String storageLocation;

    @Value("${app.image.url-path:/images/}") // application.yml 경로 사용
    private String imageUrlPath;

    // 생성자 주입 (RequiredArgsConstructor 대체)
    public MenuService(MenuRepository menuRepository, StoreRepository storeRepository) {
        this.menuRepository = menuRepository;
        this.storeRepository = storeRepository;
    }

    // 애플리케이션 시작 시 이미지 저장 디렉터리 생성 (로컬 저장 시 필요)
    @PostConstruct
    public void initStorageDirectory() {
        Path path = Paths.get(storageLocation);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                System.out.println("--- Local Image Storage: Created directory: " + storageLocation + " ---");
            } catch (IOException e) {
                System.err.println("--- Local Image Storage: Failed to create directory: " + storageLocation + " - " + e.getMessage() + " ---");
                // 실제 애플리케이션에서는 디렉터리 생성 실패 시 적절한 오류 처리 필요
            }
        }
    }

    // --- 로컬 이미지 저장/삭제 내부/헬퍼 메서드 ---

    // 이미지를 로컬 파일 시스템에 저장하고 저장된 파일의 웹 접근 경로(URL Path)를 Mono<String>으로 반환
    // 이미지가 없거나 저장 실패 시 Mono.empty() 또는 오류 반환
    private Mono<String> uploadImageToStorage(@Nullable FilePart imageFile) {
        System.out.println("--- Local Image Storage: uploadImageToStorage called ---");

        String originalFilename = Optional.ofNullable(imageFile)
                .map(FilePart::filename)
                .orElse("");

        if (imageFile == null || originalFilename.isEmpty()) {
            System.out.println("--- Local Image Storage: No valid image file provided, returning Mono.empty() ---");
            return Mono.empty(); // 유효한 이미지가 없으면 Mono.empty() 반환
        }

        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            fileExtension = originalFilename.substring(dotIndex);
        }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        Path filePath = Paths.get(storageLocation).resolve(uniqueFilename);

        System.out.println("--- Local Image Storage: Attempting to save file to: " + filePath + " ---");

        return DataBufferUtils.write(imageFile.content(), filePath, StandardOpenOption.CREATE)
                .then(Mono.just(imageUrlPath + uniqueFilename)) // 파일 쓰기 완료 후 URL 반환
                .onErrorResume(IOException.class, e -> {
                    System.err.println("--- Local Image Storage: File write IOException caught in onErrorResume: " + e.getMessage() + " ---");
                    return Mono.error(new RuntimeException("Failed to save image file", e));
                })
                .doOnError(e -> System.err.println("--- Local Image Storage: uploadImageToStorage chain onError before return: " + e.getMessage() + " --- Exception Type: " + e.getClass().getName() + " ---")); // 최종 오류 로깅
    }

    // 이미지 파일 삭제 메서드 (로컬 파일 경로 또는 URL 경로를 인자로 받아 삭제)
    private Mono<Void> deleteImageFile(String imageUrlOrPath) {
        System.out.println("--- Local Image Storage: deleteImageFile called with: " + imageUrlOrPath + " ---");

        if (imageUrlOrPath == null || imageUrlOrPath.isEmpty() || !imageUrlOrPath.startsWith(imageUrlPath)) {
            System.out.println("--- Local Image Storage: Not a local image URL, skipping deletion: " + imageUrlOrPath + " ---");
            return Mono.empty(); // 로컬 이미지 URL이 아니면 삭제하지 않음
        }

        String relativePath = imageUrlOrPath.substring(imageUrlPath.length());
        Path filePath = Paths.get(storageLocation).resolve(relativePath);

        System.out.println("--- Local Image Storage: Attempting to delete file: " + filePath + " ---");

        return Mono.fromCallable(() -> {
                    boolean deleted = Files.deleteIfExists(filePath);
                    if (deleted) {
                        System.out.println("--- Local Image Storage: File deleted successfully: " + filePath + " ---");
                    } else {
                        System.out.println("--- Local Image Storage: File not found for deletion: " + filePath + " ---");
                    }
                    return deleted;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(); // Mono<Void>로 변환
    }


    // --- 가게 주인 권한 확인 헬퍼 메서드 ---
    private Mono<Boolean> isUserAuthorizedToManageStore(String userId, Long storeId) {
        System.out.println("--- MenuService: 권한 확인 시도 (UserID: " + userId + ", StoreID: " + storeId + ") ---");
        if (userId == null) {
            System.err.println("--- MenuService: User ID is null, cannot check authorization ---");
            return Mono.just(false); // 사용자 ID가 null이면 권한 없음
        }
        return storeRepository.findById(storeId)
                .map(store -> {
                    boolean isOwner = userId.equals(store.getOwnerId());
                    System.out.println("--- MenuService: 권한 확인 결과: " + isOwner + " ---");
                    return isOwner;
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    System.err.println("--- MenuService: 권한 확인 중 DB 조회 오류: " + e.getMessage() + " ---");
                    return Mono.just(false);
                });
    }


    // --- 실제 서비스 메서드 구현 ---

    // POST: 새로운 메뉴 생성
    public Mono<Menu> createMenu(Menu menu, @Nullable FilePart imageFile, String userId) {
        System.out.println("--- MenuService: createMenu 호출 ---");

        if (menu.getStoreId() == null) {
            return Mono.error(new IllegalArgumentException("Store ID is required for a new menu."));
        }

        return isUserAuthorizedToManageStore(userId, menu.getStoreId())
                .flatMap(isAuthorized -> {
                    if (!isAuthorized) {
                        return Mono.error(new SecurityException("User is not authorized to add a menu to this store."));
                    }
                    System.out.println("--- MenuService: 권한 확인 통과, 메뉴 생성 계속 ---");

                    // 이미지 업로드 (선택 사항)
                    Mono<String> imageUrlMono = uploadImageToStorage(imageFile)
                            .switchIfEmpty(Mono.empty()); // 이미지가 없으면 empty() 발행

                    // 이미지 업로드 결과에 따라 메뉴 객체 설정 및 저장
                    return imageUrlMono
                            .doOnNext(imageUrl -> { // 이미지가 있는 경우에만 실행
                                System.out.println("--- MenuService: 이미지 URL 획득 (" + imageUrl + "), 메뉴 정보 설정 시작 ---");
                                menu.setImageUrl(imageUrl); // 이미지 URL 설정
                                menu.setCreatedAt(LocalDateTime.now()); // 생성 시간 설정
                                menu.setUpdatedAt(LocalDateTime.now()); // 수정 시간 설정
                            })
                            .flatMap(imageUrl -> { // 이미지가 있는 경우에만 실행 (imageUrl은 사용되지 않음)
                                System.out.println("--- MenuService: 메뉴 정보 (with imageUrl) DB 저장 시작 ---");
                                return menuRepository.save(menu); // imageUrl이 null이 아닌 상태로 save
                            })
                            .switchIfEmpty(Mono.defer(() -> { // 이미지가 없는 경우 (imageUrlMono가 empty인 경우)에만 실행
                                System.out.println("--- MenuService: 이미지 없음, 메뉴 정보 (imageUrl=null) DB 저장 시작 ---");
                                menu.setCreatedAt(LocalDateTime.now()); // 생성 시간 설정
                                menu.setUpdatedAt(LocalDateTime.now()); // 수정 시간 설정
                                // imageUrl은 기본값(null) 그대로입니다.
                                return menuRepository.save(menu); // imageUrl이 null인 상태 그대로 save
                            }));
                })
                .doOnSuccess(savedMenu -> System.out.println("--- MenuService: 메뉴 생성 성공 (ID: " + savedMenu.getId() + ") ---"))
                .doOnError(e -> System.err.println("--- MenuService: 메뉴 생성 중 오류 발생: " + e.getMessage() + " --- Exception Type: " + e.getClass().getName() + " ---"));
    }


    // PUT: 메뉴 수정
    public Mono<Menu> updateMenu(Long menuId, Menu updatedMenu, @Nullable FilePart newImageFile, String userId) {
        System.out.println("--- MenuService: updateMenu 호출 (MenuID: " + menuId + ", UserID: " + userId + ") ---");

        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")))
                .flatMap(existingMenu -> {
                    System.out.println("--- MenuService: 기존 메뉴 조회 완료 (ID: " + existingMenu.getId() + ", StoreID: " + existingMenu.getStoreId() + ") ---");
                    Long storeId = existingMenu.getStoreId();
                    return isUserAuthorizedToManageStore(userId, storeId)
                            .flatMap(isAuthorized -> {
                                if (!isAuthorized) {
                                    return Mono.error(new SecurityException("User is not authorized to update this menu."));
                                }
                                System.out.println("--- MenuService: 권한 확인 통과, 메뉴 수정 계속 ---");

                                // 새 이미지 파일 업로드 시도 (선택 사항)
                                Mono<String> newImageUrlOrSignalMono = uploadImageToStorage(newImageFile)
                                        .defaultIfEmpty("KEEP_EXISTING_IMAGE_SIGNAL"); // 새 이미지가 없으면 신호값 발행

                                return newImageUrlOrSignalMono.doOnNext(newImageUrlOrSignal -> {
                                            String finalImageUrl;
                                            String oldImageUrl = existingMenu.getImageUrl();

                                            if ("KEEP_EXISTING_IMAGE_SIGNAL".equals(newImageUrlOrSignal)) {
                                                System.out.println("--- MenuService: 새 이미지 없음, 기존 이미지 유지 ---");
                                                finalImageUrl = oldImageUrl;
                                            } else {
                                                System.out.println("--- MenuService: 새 이미지 업로드 완료, 기존 이미지 교체 ---");
                                                finalImageUrl = newImageUrlOrSignal;

                                                if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
                                                    System.out.println("--- Local Image Storage: 기존 이미지 파일 삭제 시도: " + oldImageUrl + " ---");
                                                    deleteImageFile(oldImageUrl).subscribe(); // 비동기 삭제 (결과 기다리지 않음)
                                                }
                                            }

                                            // existingMenu 객체에 updatedMenu의 변경 사항 반영
                                            existingMenu.setName(updatedMenu.getName());
                                            existingMenu.setDescription(updatedMenu.getDescription());
                                            existingMenu.setPrice(updatedMenu.getPrice());
                                            existingMenu.setAvailable(updatedMenu.isAvailable());
                                            existingMenu.setImageUrl(finalImageUrl);
                                            existingMenu.setUpdatedAt(LocalDateTime.now());
                                        })
                                        .flatMap(newImageUrlOrSignal -> { // newImageUrlOrSignal는 여기서 사용되지 않음
                                            System.out.println("--- MenuService: 업데이트된 메뉴 정보 DB 저장 시작 ---");
                                            return menuRepository.save(existingMenu);
                                        });
                            });
                })
                .doOnSuccess(savedMenu -> System.out.println("--- MenuService: 메뉴 수정 성공 (ID: " + savedMenu.getId() + ", ImageUrl: " + savedMenu.getImageUrl() + ") ---"))
                .doOnError(e -> System.err.println("--- MenuService: 메뉴 수정 중 오류 발생: " + e.getMessage() + " --- Exception Type: " + e.getClass().getName() + " ---"));
    }


    // DELETE: 메뉴 삭제
    public Mono<Void> deleteMenu(Long menuId, String userId) {
        System.out.println("--- MenuService: deleteMenu 호출 (MenuID: " + menuId + ", UserID: " + userId + ") ---");
        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")))
                .flatMap(existingMenu -> {
                    System.out.println("--- MenuService: 기존 메뉴 조회 완료 (ID: " + existingMenu.getId() + ", StoreID: " + existingMenu.getStoreId() + ") ---");
                    Long storeId = existingMenu.getStoreId();
                    return isUserAuthorizedToManageStore(userId, storeId)
                            .flatMap(isAuthorized -> {
                                if (!isAuthorized) {
                                    return Mono.error(new SecurityException("User is not authorized to delete this menu."));
                                }
                                System.out.println("--- MenuService: 권한 확인 통과, 메뉴 삭제 계속 ---");

                                // 해당 메뉴의 이미지 파일 삭제 (선택 사항)
                                String imageUrl = existingMenu.getImageUrl();
                                Mono<Void> deleteImageMono = deleteImageFile(imageUrl);

                                // 메뉴 정보 DB 삭제 후 이미지 삭제
                                System.out.println("--- MenuService: 메뉴 정보 DB 삭제 시작 ---");
                                return menuRepository.deleteById(menuId)
                                        .then(deleteImageMono); // DB 삭제 완료 후 이미지 삭제 실행 및 완료를 기다림
                            });
                })
                .doOnSuccess(v -> System.out.println("--- MenuService: 메뉴 삭제 성공 (ID: " + menuId + ") ---"))
                .doOnError(e -> System.err.println("--- MenuService: 메뉴 삭제 중 오류 발생: " + e.getMessage() + " --- Exception Type: " + e.getClass().getName() + " ---"));
    }


    // GET: ID로 메뉴 조회
    public Mono<Menu> findMenuById(Long menuId) {
        System.out.println("--- MenuService: findMenuById 호출 (MenuID: " + menuId + ") ---");
        return menuRepository.findById(menuId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu with ID " + menuId + " not found.")));
    }

    // GET: StoreId로 메뉴 목록 조회
    public Flux<Menu> findMenusByStoreId(Long storeId) {
        System.out.println("--- MenuService: findMenusByStoreId 호출 (StoreID: " + storeId + ") ---");
        return menuRepository.findByStoreId(storeId);
    }
}
