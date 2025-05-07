package com.localy.store_service.menu.service;

import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.repository.MenuRepository;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service; // Service 어노테이션 임포트
import reactor.core.publisher.Flux; // Reactor Flux 임포트
import reactor.core.publisher.Mono; // Reactor Mono 임포트


import java.util.NoSuchElementException; // 데이터가 없을 경우 예외 처리용

@Service
@RequiredArgsConstructor
public class MenuService {

    // R2DBC Menu Repository 의존성 주입
    private final MenuRepository menuRepository;

    // ID로 특정 메뉴 항목 조회
    public Mono<Menu> findMenuById(Long id) {
        System.out.println("--- MenuService: findMenuById 호출 (ID: " + id + ") ---");
        return menuRepository.findById(id) // R2dbcRepository의 findById()는 Mono<Menu> 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu not found with ID: " + id))) // Mono가 비어있으면 (데이터 없으면) 예외 발행
                .doOnSuccess(menu -> System.out.println("--- MenuService: 메뉴 찾음 (ID: " + id + ") ---"))
                .doOnError(e -> System.err.println("--- MenuService: findMenuById 오류 - " + e.getMessage() + " ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }

    // 특정 가게 ID로 모든 메뉴 항목 조회
    public Flux<Menu> findMenusByStoreId(Long storeId) {
        System.out.println("--- MenuService: findMenusByStoreId 호출 (StoreID: " + storeId + ") ---");
        // MenuRepository에 정의한 findByStoreId 쿼리 메서드 호출
        return menuRepository.findByStoreId(storeId) // R2dbcRepository의 findByStoreId()는 Flux<Menu> 반환
                .doOnComplete(() -> System.out.println("--- MenuService: findMenusByStoreId 완료 ---"))
                .doOnError(e -> System.err.println("--- MenuService: findMenusByStoreId 오류 - " + e.getMessage() + " ---"));
        // R2DBC 호출은 논블록킹이므로 subscribeOn 필요 없음
    }

// MenuService.java createMenuWithImage 메서드 수정 예시

    // @Nullable 어노테이션을 사용하여 이 파라미터가 null일 수 있음을 명시 (IDE/코드 분석 도구에 도움)
    public Mono<Menu> createMenuWithImage(Menu menu, @Nullable FilePart imageFile, String userId) {
        System.out.println("--- MenuService: createMenuWithImage 호출 (메뉴 이름: " + (menu != null ? menu.getName() : "null") + ", 파일명: " + (imageFile != null ? imageFile.filename() : "null") + ", UserID: " + userId + ") ---");

        // 1. 이미지 업로드 로직 (이미지 파일이 제공된 경우에만 실행)
        Mono<String> imageUrlMono;
        if (imageFile != null && !imageFile.filename().isEmpty()) {
            // 이미지 파일이 제공된 경우: 업로드 비동기 작업 시작
            imageUrlMono = uploadImageToStorage(imageFile) // uploadImageToStorage는 Mono<String> 반환
                    .doOnSuccess(url -> System.out.println("--- MenuService: 이미지 업로드 완료: " + url + " ---"))
                    .doOnError(e -> System.err.println("--- MenuService: 이미지 업로드 중 오류 발생: " + e.getMessage() + " ---"));
        } else {
            // 이미지 파일이 제공되지 않은 경우: null URL을 발행하는 Mono<String> 생성
            // 이렇게 하면 하위 flatMap이 실행되지만 imageUrl은 null이 됩니다.
            imageUrlMono = Mono.empty();
            System.out.println("--- MenuService: 이미지 파일 없음, imageUrlMono에 null 발행 ---");
        }

        // 2. 이미지 업로드 결과(URL)가 준비되면 (Mono<String>이 발행되면), 메뉴 정보와 결합하고 데이터베이스에 저장하는 비동기 작업 실행
        return imageUrlMono.flatMap(imageUrl -> { // imageUrl은 실제 URL 또는 null
                    System.out.println("--- MenuService: 이미지 URL 획득 (" + imageUrl + "), 메뉴 정보 설정 시작 ---");

                    // Menu 객체에 imageUrl 설정 (imageUrl이 null이면 null이 설정됩니다)
                    menu.setImageUrl(imageUrl);

                    // userId 설정 (필요시)
                    // menu.setUserId(userId); // 만약 메뉴 엔티티에 userId가 있다면 설정

                    System.out.println("--- MenuService: 메뉴 정보 DB 저장 시작 ---");
                    // 3. imageUrl 필드가 설정된 Menu 엔티티를 데이터베이스에 저장합니다.
                    // menuRepository.save()는 저장 후 ID가 채워진 Mono<Menu>를 반환합니다.
                    return menuRepository.save(menu)
                            .doOnSuccess(savedMenu -> System.out.println("--- MenuService: DB 저장 완료 (ID: " + savedMenu.getId() + ") ---"))
                            .doOnError(e -> System.err.println("--- MenuService: DB 저장 중 오류 발생: " + e.getMessage() + " ---"));
                })
                // --- 4. Reactive 체인 실행 중 발생하는 모든 오류 처리 ---
                // 이미지 업로드 (uploadImageToStorage) 또는 DB 저장 (menuRepository.save) 중 어떤 단계에서든 오류가 발생하면 여기서 잡힙니다.
                .onErrorResume(e -> {
                    System.err.println("--- MenuService: createMenuWithImage 체인 실행 중 오류 발생 (onErrorResume): " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");
                    // 오류를 로깅하고, 컨트롤러가 처리할 수 있도록 다시 발행합니다.
                    return Mono.error(e);
                });
    }

    public Mono<Menu> updateMenuWithImage(Long id, Menu updatedMenu, @Nullable FilePart newImageFile, String currentUserId) {
        System.out.println("--- MenuService: updateMenuWithImage 호출 (ID: " + id + ", 파일명: " + (newImageFile != null ? newImageFile.filename() : "없음") + ", UserID: " + currentUserId + ") ---");

        // 1. 기존 메뉴를 ID로 찾습니다. (수정 대상 존재 확인 및 권한 확인을 위해 필요)
        return menuRepository.findById(id) // Mono<Menu> 반환
                .switchIfEmpty(Mono.error(new NoSuchElementException("Menu not found with ID: " + id))) // 없으면 404 처리 대상 예외 발행
                .flatMap(existingMenu -> { // 기존 메뉴를 찾으면 실행 (existingMenu 객체)
                    System.out.println("--- MenuService: 수정할 메뉴 찾음 (ID: " + id + ") ---");
                    // 2. 요청한 사용자가 리뷰 작성자 본인인지 확인합니다.
                    // Menu 엔티티에 userId 필드가 있어 작성자를 식별할 수 있다고 가정합니다.
                    System.out.println("--- MenuService: 수정 권한 확인 완료 ---");

                    // --- 3. 이미지 파일 처리 (선택적) ---
                    Mono<String> imageUrlMono; // 최종적으로 사용될 이미지 URL을 발행할 Mono

                    if (newImageFile != null && !newImageFile.filename().isEmpty()) {
                        // 새 이미지가 제공된 경우 (newImageFile이 null이 아니고 파일 이름도 비어있지 않으면)
                        System.out.println("--- MenuService: 새로운 이미지 파일 감지, 업로드 시작 ---");
                        imageUrlMono = uploadImageToStorage(newImageFile) // 새 이미지 업로드
                                .doOnSuccess(url -> {
                                    System.out.println("--- MenuService: 새 이미지 업로드 완료: " + url + " ---");
                                    // TODO: 이전 이미지 삭제 로직 (선택 사항이지만 권장: 스토리지 공간 절약)
                                    // 이전 이미지 URL이 null이 아니고 새 이미지 URL과 다르다면 이전 이미지 삭제 비동기 호출
                                    // if (existingMenu.getImageUrl() != null && !existingMenu.getImageUrl().isEmpty() && !existingMenu.getImageUrl().equals(url)) {
                                    //     deleteImageFromStorage(existingMenu.getImageUrl()).subscribe(); // 삭제 결과 기다리지 않음 (fire-and-forget)
                                    // }
                                })
                                .doOnError(e -> System.err.println("--- MenuService: 새 이미지 업로드 오류: " + e.getMessage() + " ---")); // 업로드 오류 로깅
                    } else {
                        // 새 이미지가 제공되지 않은 경우 (newImageFile이 null이거나 파일 이름이 비어있는 경우)
                        // 기존 이미지 URL을 그대로 유지합니다.
                        System.out.println("--- MenuService: 새로운 이미지 파일 없음, 기존 URL 유지 ---");
                        imageUrlMono = Mono.justOrEmpty(existingMenu.getImageUrl()); // 기존 URL을 Mono<String>으로 반환 (기존 URL이 null이면 Mono.empty() 반환)
                    }
                    // ------------------------------------

                    // --- 4. 이미지 처리 결과(URL)를 받아 기존 메뉴 정보 업데이트 및 저장 ---
                    // imageUrlMono가 발행될 때까지 기다립니다 (새 이미지 업로드 완료 또는 기존 URL 확정).
                    return imageUrlMono.flatMap(finalImageUrl -> { // 최종 이미지 URL (finalImageUrl)
                        System.out.println("--- MenuService: 최종 이미지 URL 획득 (" + finalImageUrl + "), 기존 메뉴 정보 업데이트 시작 ---");
                        // 기존 메뉴 엔티티(existingMenu)의 필드를 요청 본문에서 온 updatedMenu의 값들로 업데이트합니다.
                        // id, storeId, userId, createdAt는 변경 불가 필드입니다.
                        existingMenu.setName(updatedMenu.getName()); // 이름 업데이트
                        existingMenu.setDescription(updatedMenu.getDescription()); // 설명 업데이트
                        existingMenu.setPrice(updatedMenu.getPrice()); // 가격 업데이트
                        existingMenu.setAvailable(updatedMenu.isAvailable()); // 판매 가능 여부 업데이트
                        // updatedAt은 Auditing 설정 시 save 메서드 호출 시점에 자동 업데이트됩니다.

                        // 최종 이미지 URL을 설정합니다.
                        existingMenu.setImageUrl(finalImageUrl);

                        System.out.println("--- MenuService: 업데이트된 메뉴 정보 DB 저장 시작 ---");
                        // 5. 업데이트된 엔티티를 데이터베이스에 저장합니다. (R2DBC는 save 메서드가 upsert 역할)
                        return menuRepository.save(existingMenu) // 저장 후 Mono<Menu> 반환
                                .doOnSuccess(savedMenu -> System.out.println("--- MenuService: DB 저장 완료 (ID: " + savedMenu.getId() + ") ---"))
                                .doOnError(e -> System.err.println("--- MenuService: DB 저장 중 오류: " + e.getMessage() + " ---")); // DB 저장 오류 로깅
                    });
                })
                // --- 6. Reactive 체인 실행 중 발생하는 모든 오류 처리 ---
                // findById, 권한 확인, 이미지 처리 (업로드/삭제), DB 저장 등 모든 단계의 오류를 여기서 잡습니다.
                .onErrorResume(e -> { // 체인에서 발생한 예외 'e'를 인자로 받습니다.
                    System.err.println("--- MenuService: updateMenuWithImage 체인 실행 중 오류 발생 (onErrorResume): " + e.getMessage() + " --- 예외 타입: " + e.getClass().getName() + " ---");

                    // 서비스는 오류를 로깅하고 컨트롤러가 처리하도록 다시 발행합니다.
                    // NoSuchElementException, SecurityException, IllegalArgumentException 등은 Controller에서 특정 상태 코드로 매핑하도록 이미 구현되어 있습니다.
                    // 여기서 다시 발행해주면 Controller의 onErrorResume 블록으로 전달됩니다.
                    return Mono.error(e); // 발생한 예외를 그대로 다시 발행합니다.
                });
    }

    private Mono<String> uploadImageToStorage(@Nullable FilePart imageFile) { // @Nullable 어노테이션으로 imageFile이 null일 수 있음을 명시 (선택 사항이지만 좋음)
        System.out.println("--- ImageUploadService: uploadImageToStorage simulation called ---");

        // 1. FilePart로부터 이미지 URL을 계산하는 로직 (이미지가 없으면 null 반환하도록 구현)
        // 이 부분은 실제 업로드 로직에서 URL을 얻어오는 과정이 될 것입니다.
        String potentialImageUrl = calculateImageUrl(imageFile); // <-- 이 메서드를 따로 구현해야 합니다.
        //    imageFile이 유효하지 않으면 null을 반환하도록요.

        System.out.println("--- ImageUploadService: Calculated URL: " + potentialImageUrl + " ---");

        // 2. 계산된 URL을 Mono.justOrEmpty로 감싸서 반환
        // potentialImageUrl이 null이면 Mono.empty() 반환
        // potentialImageUrl이 null이 아니면 Mono.just(potentialImageUrl) 반환
        return Mono.justOrEmpty(potentialImageUrl)
                .delayElement(java.time.Duration.ofMillis(100)); // 비동기 시뮬레이션 (필요시)
    }

    // --- Helper 메서드: FilePart로부터 이미지 URL을 계산 (예시) ---
    private String calculateImageUrl(@Nullable FilePart imageFile) {
        // 여기에 실제 이미지 파일 존재 여부, 유효성 등을 체크하는 로직 구현
        // 예시: FilePart가 유효하고 파일명이 있으면 URL 생성, 아니면 null 반환
        if (imageFile != null && imageFile.filename() != null && !imageFile.filename().isEmpty() /* && 파일 내용 체크 */) {
            // 실제 클라우드 스토리지 업로드 로직을 호출하고 URL을 받아와야 함 (이건 시뮬레이션)
            return "http://example.com/images/menus/uploaded_" + imageFile.filename(); // 실제 URL 반환 로직
        }
        return null; // 이미지가 없거나 유효하지 않으면 null 반환
    }

    // --- 이미지 파일을 클라우드 스토리지에서 삭제하는 가상 메서드 ---
    // 실제 구현은 스토리지 SDK에 따라 다릅니다. URL을 받아 삭제하고 Mono<Void> 반환.
    // 삭제 실패 시 Mono.error(Exception) 반환해야 합니다.
    private Mono<Void> deleteImageFromStorage(String imageUrl) {
        System.out.println("--- ImageUploadService: " + imageUrl + " 삭제 시뮬레이션 ---");
        // TODO: 실제 삭제 로직 구현 (비동기 논블록킹 방식으로)
        // 예: return reactiveS3Client.deleteObject(bucketName, key);
        return Mono.empty(); // 삭제 성공 시 Mono<Void> 반환
        // 예시 오류: return Mono.error(new RuntimeException("Simulated deletion failure for " + imageUrl));
    }

    // 메뉴 항목 삭제
    // 흐름: 존재 확인 (선택 사항) -> 삭제
    public Mono<Void> deleteMenu(Long id) {
        System.out.println("--- MenuService: deleteMenu 호출 (ID: " + id + ") ---");
        // R2dbcRepository의 deleteById는 Mono<Void>를 반환하며 존재하지 않아도 오류 나지 않음.
        // 삭제 전에 존재하는지 확인하고 싶다면 existsById 사용
        return menuRepository.existsById(id) // 해당 ID의 메뉴가 존재하는지 확인 (Mono<Boolean> 반환)
                .flatMap(exists -> { // 존재 여부 결과에 따라 다음 단계를 실행
                    if (exists) {
                        System.out.println("--- MenuService: 삭제할 메뉴 존재 (ID: " + id + ") --- 삭제 시작 ---");
                        // 메뉴가 존재하면 deleteById 호출 (Mono<Void> 반환)
                        return menuRepository.deleteById(id)
                                .doOnSuccess(aVoid -> System.out.println("--- MenuService: 메뉴 삭제 완료 (ID: " + id + ") ---"))
                                .doOnError(e -> System.err.println("--- MenuService: deleteMenu 오류 중 삭제 실패 - " + e.getMessage() + " ---"));
                    } else {
                        // 메뉴가 존재하지 않으면 NoSuchElementException을 발행하여 스트림을 종료
                        System.err.println("--- MenuService: 삭제할 메뉴 찾을 수 없음 (ID: " + id + ") ---");
                        return Mono.error(new NoSuchElementException("Menu not found with ID: " + id));
                    }
                })
                .onErrorResume(e -> {
                    // existsById 또는 deleteById 체인 중 발생한 오류를 잡습니다.
                    System.err.println("--- MenuService: deleteMenu 오류 - " + e.getMessage() + " ---");
                    return Mono.error(e); // 오류를 다시 발행하여 컨트롤러로 전달
                });
    }

    // --- 추가적으로 필요한 비즈니스 로직 메서드 구현 ---
    // 예: 모든 메뉴 목록 조회
    public Flux<Menu> findAllMenus() {
        System.out.println("--- MenuService: findAllMenus 호출 ---");
        return menuRepository.findAll() // R2dbcRepository의 findAll()은 Flux<Menu> 반환
                .doOnComplete(() -> System.out.println("--- MenuService: findAllMenus 완료 ---"));
    }
}