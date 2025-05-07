package com.localy.store_service.menu.service;

// JUnit 5 및 Mockito 임포트
import io.micrometer.common.lang.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
// Reactive Stream 임포트
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier; // Reactive Stream 테스트용

// Spring 관련 임포트
import org.springframework.http.codec.multipart.FilePart; // FilePart 모킹용

// 테스트 대상 Repository 및 엔티티, 예외 임포트
import com.localy.store_service.menu.domain.Menu;
import com.localy.store_service.menu.repository.MenuRepository;

// 기타 유틸리티 임포트
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.time.LocalDateTime;
import java.math.BigDecimal; // BigDecimal 임포트

// FilePart 모킹을 위한 추가 임포트 (Mock 객체 생성)
import static org.mockito.Mockito.mock;


// MockitoExtension을 사용하여 JUnit 5에서 Mockito 어노테이션 활성화
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    // MenuRepository를 Mock 객체로 만듭니다.
    @Mock
    private MenuRepository menuRepository;

    // @InjectMocks를 사용하여 Mock 객체들이 MenuService 인스턴스에 주입되도록 합니다.
    @InjectMocks
    private MenuService menuService;

    // --- 테스트 유틸리티 메서드: 테스트용 Menu 객체 생성 ---
    private Menu createTestMenu(Long id, Long storeId, String name, String imageUrl) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setStoreId(storeId);
        menu.setName(name);
        menu.setDescription("Description for " + name);
        menu.setPrice(BigDecimal.valueOf(15000.00));
        menu.setImageUrl(imageUrl); // 이미지 URL 설정
        menu.setAvailable(true);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        return menu;
    }

    // --- 테스트 유틸리티 메서드: Mock FilePart 객체 생성 ---
    private FilePart createMockFilePart(String filename) {
        FilePart mockFilePart = mock(FilePart.class);
        when(mockFilePart.filename()).thenReturn(filename);
        // 필요하다면 transferTo 등 다른 메서드도 모킹할 수 있습니다.
        return mockFilePart;
    }


    // --- findMenuById 테스트 ---
    @Test
    void findMenuById_ShouldReturnMenu_WhenFound() {
        // given
        Long menuId = 1L;
        Menu mockMenu = createTestMenu(menuId, 1L, "Test Menu", "url");
        // findById가 호출될 때 mockMenu를 발행하는 Mono 반환하도록 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.just(mockMenu));

        // when
        Mono<Menu> resultMono = menuService.findMenuById(menuId);

        // then
        // StepVerifier를 사용하여 Reactive Stream의 결과를 검증
        StepVerifier.create(resultMono)
                .expectNext(mockMenu) // 예상되는 데이터가 발행되는지 검증
                .verifyComplete(); // 스트림이 정상적으로 완료되는지 검증

        // findById 메서드가 올바른 ID로 호출되었는지 Mockito verify
        verify(menuRepository).findById(menuId);
    }

    @Test
    void findMenuById_ShouldThrowNoSuchElementException_WhenNotFound() {
        // given
        Long menuId = 999L;
        // findById가 호출될 때 아무 데이터도 발행하지 않고 완료되는 빈 Mono 반환하도록 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.empty());

        // when
        Mono<Menu> resultMono = menuService.findMenuById(menuId);

        // then
        // StepVerifier를 사용하여 Reactive Stream의 결과를 검증
        StepVerifier.create(resultMono)
                .expectError(NoSuchElementException.class) // 예상되는 예외가 발행되는지 검증
                .verify(); // 스트림이 오류와 함께 완료되는지 검증

        // findById 메서드가 올바른 ID로 호출되었는지 Mockito verify
        verify(menuRepository).findById(menuId);
    }

    @Test
    void findMenuById_ShouldPropagateError_WhenRepositoryErrors() {
        // given
        Long menuId = 1L;
        RuntimeException repositoryError = new RuntimeException("DB connection failed");
        // findById가 호출될 때 오류를 발행하는 Mono 반환하도록 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.error(repositoryError));

        // when
        Mono<Menu> resultMono = menuService.findMenuById(menuId);

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("DB connection failed")) // 예상되는 오류와 메시지 검증
                .verify();

        verify(menuRepository).findById(menuId);
    }


    // --- findMenusByStoreId 테스트 ---
    @Test
    void findMenusByStoreId_ShouldReturnMenus_WhenFound() {
        // given
        Long storeId = 1L;
        Menu menu1 = createTestMenu(1L, storeId, "Menu 1", "url1");
        Menu menu2 = createTestMenu(2L, storeId, "Menu 2", "url2");
        List<Menu> mockMenus = Arrays.asList(menu1, menu2);
        // findByStoreId 호출 시 Flux<Menu> 반환하도록 Mocking
        when(menuRepository.findByStoreId(storeId)).thenReturn(Flux.fromIterable(mockMenus));

        // when
        Flux<Menu> resultFlux = menuService.findMenusByStoreId(storeId);

        // then
        StepVerifier.create(resultFlux)
                .expectNext(menu1, menu2) // 예상되는 데이터들이 순서대로 발행되는지 검증
                .verifyComplete(); // 스트림이 정상적으로 완료되는지 검증

        // findByStoreId 메서드가 올바른 Store ID로 호출되었는지 Mockito verify
        verify(menuRepository).findByStoreId(storeId);
    }

    @Test
    void findMenusByStoreId_ShouldReturnEmptyFlux_WhenNotFound() {
        // given
        Long storeId = 999L;
        // findByStoreId 호출 시 빈 Flux 반환하도록 Mocking
        when(menuRepository.findByStoreId(storeId)).thenReturn(Flux.empty());

        // when
        Flux<Menu> resultFlux = menuService.findMenusByStoreId(storeId);

        // then
        StepVerifier.create(resultFlux)
                .expectNextCount(0) // 발행되는 데이터가 없는지 검증
                .verifyComplete(); // 스트림이 정상적으로 완료되는지 검증 (빈 Flux는 데이터 없이 완료)

        verify(menuRepository).findByStoreId(storeId);
    }

    @Test
    void findMenusByStoreId_ShouldPropagateError_WhenRepositoryErrors() {
        // given
        Long storeId = 1L;
        RuntimeException repositoryError = new RuntimeException("Network error");
        // findByStoreId 호출 시 오류 발행하도록 Mocking
        when(menuRepository.findByStoreId(storeId)).thenReturn(Flux.error(repositoryError));

        // when
        Flux<Menu> resultFlux = menuService.findMenusByStoreId(storeId);

        // then
        StepVerifier.create(resultFlux)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Network error"))
                .verify();

        verify(menuRepository).findByStoreId(storeId);
    }


    // --- createMenuWithImage 테스트 (이미지 선택 사항 처리) ---
    @Test
    void createMenuWithImage_ShouldSaveMenu_WithImage_WhenImageProvided() {
        // given
        Long storeId = 1L;
        String userId = "user123";
        Menu menuToCreate = createTestMenu(null, storeId, "New Menu With Image", null); // 생성 전에는 ID, URL 없음
        FilePart mockImageFile = createMockFilePart("test.jpg");
        Menu savedMenu = createTestMenu(10L, storeId, "New Menu With Image", "http://example.com/images/menus/test.jpg"); // 저장 후 ID, URL 생김

        // menuRepository.save가 호출될 때 savedMenu를 발행하는 Mono 반환하도록 Mocking
        // any(Menu.class) 매처는 어떤 Menu 객체가 save의 인자로 오든 매칭
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.just(savedMenu));

        // when
        // createMenuWithImage 메서드에 메뉴 정보, Mock FilePart, userId 전달
        Mono<Menu> resultMono = menuService.createMenuWithImage(menuToCreate, mockImageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectNext(savedMenu) // 서비스가 반환한 저장된 메뉴 객체 검증
                .verifyComplete();

        // menuRepository.save가 호출되었는지 확인
        // save 메서드에 전달된 Menu 객체의 imageUrl이 null이 아닌 값인지 검증 (Argument Captor 사용하면 더 상세 검증 가능)
        verify(menuRepository).save(any(Menu.class)); // save 호출 확인
        // save 메서드가 호출될 때 Menu 객체의 imageUrl이 null이 아닌 값으로 설정되었는지 간접적으로 검증
        // Mockito의 Argument Captor를 사용하면 save 호출 시 실제 전달된 Menu 객체를 캡처해서 검증할 수 있습니다.
        // 여기서는 간단히 any(Menu.class)로 save 호출 확인만 합니다.
    }

    @Test
    void createMenuWithImage_ShouldSaveMenu_WithoutImage_WhenImageNotProvided() {
        // given
        Long storeId = 1L;
        String userId = "user123";
        Menu menuToCreate = createTestMenu(null, storeId, "New Menu Without Image", null); // 생성 전에는 ID, URL 없음
        // 이미지 파일은 null로 전달
        FilePart imageFile = null;
        Menu savedMenu = createTestMenu(10L, storeId, "New Menu Without Image", null); // 저장 후 ID 생기지만 URL은 null

        // menuRepository.save가 호출될 때 savedMenu를 발행하는 Mono 반환하도록 Mocking
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.just(savedMenu));

        // when
        // createMenuWithImage 메서드에 메뉴 정보, null FilePart, userId 전달
        Mono<Menu> resultMono = menuService.createMenuWithImage(menuToCreate, imageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectNext(savedMenu) // 서비스가 반환한 저장된 메뉴 객체 검증
                .verifyComplete();

        // menuRepository.save가 호출되었는지 확인
        verify(menuRepository).save(any(Menu.class));
        // save 메서드가 호출될 때 Menu 객체의 imageUrl이 null 값으로 설정되었는지 간접적으로 검증
        // Argument Captor 필요 (생략)
    }

    @Test
    void createMenuWithImage_ShouldPropagateError_WhenRepositoryErrors() {
        // given
        Long storeId = 1L;
        String userId = "user123";
        Menu menuToCreate = createTestMenu(null, storeId, "Menu Error", null);
        FilePart mockImageFile = createMockFilePart("error.jpg"); // 이미지가 있든 없든 테스트 가능
        RuntimeException repositoryError = new RuntimeException("DB save failed");

        // menuRepository.save가 호출될 때 오류 발행하도록 Mocking
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.error(repositoryError));

        // when
        Mono<Menu> resultMono = menuService.createMenuWithImage(menuToCreate, mockImageFile, userId); // 또는 imageFile=null

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("DB save failed"))
                .verify();

        verify(menuRepository).save(any(Menu.class));
    }


    // --- updateMenuWithImage 테스트 ---
    @Test
    void updateMenuWithImage_ShouldUpdateMenu_WithoutNewImage_WhenNoNewImageProvided() {
        // given
        Long menuId = 1L;
        Long storeId = 1L;
        String userId = "user123";
        Menu existingMenu = createTestMenu(menuId, storeId, "Original Name", "http://example.com/old.jpg");
        Menu updatedMenuData = createTestMenu(null, storeId, "Updated Name", null);
        // 새 이미지 파일은 null로 전달합니다. @Nullable 어노테이션은 제거합니다.
        FilePart newImageFile = null; // <-- @Nullable 어노테이션 제거
        Menu savedMenu = createTestMenu(menuId, storeId, "Updated Name", "http://example.com/old.jpg");

        // findById 호출 시 기존 메뉴 반환 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.just(existingMenu));
        // save 호출 시 저장될 메뉴 반환 Mocking
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.just(savedMenu));

        // when
        Mono<Menu> resultMono = menuService.updateMenuWithImage(menuId, updatedMenuData, newImageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectNext(savedMenu)
                .verifyComplete();

        verify(menuRepository).findById(menuId);
        verify(menuRepository).save(any(Menu.class));
    }

    @Test
    void updateMenuWithImage_ShouldUpdateMenu_WithNewImage_WhenNewImageProvided() {
        // given
        Long menuId = 1L;
        Long storeId = 1L;
        String userId = "user123";
        Menu existingMenu = createTestMenu(menuId, storeId, "Original Name", "http://example.com/old.jpg"); // 기존 메뉴
        Menu updatedMenuData = createTestMenu(null, storeId, "Updated Name", null); // 업데이트 요청 데이터
        FilePart mockNewImageFile = createMockFilePart("new.png"); // 새 이미지 파일
        Menu savedMenu = createTestMenu(menuId, storeId, "Updated Name", "http://example.com/images/menus/new.png"); // 저장될 메뉴 (새 URL 포함)

        // findById 호출 시 기존 메뉴 반환 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.just(existingMenu));
        // save 호출 시 저장될 메뉴 반환 Mocking
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.just(savedMenu));

        // when
        Mono<Menu> resultMono = menuService.updateMenuWithImage(menuId, updatedMenuData, mockNewImageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectNext(savedMenu)
                .verifyComplete();

        verify(menuRepository).findById(menuId);
        verify(menuRepository).save(any(Menu.class));
        // uploadImageToStorage 메서드는 호출되어야 함 (private 메서드지만, 로직상 호출되는 경로이므로 간접 확인)
        // deleteImageFromStorage 메서드는 기존 URL이 있고 새 URL이 다르면 호출될 수 있음 (서비스 로직 및 테스트 설계에 따라 검증 추가)
    }


    @Test
    void updateMenuWithImage_ShouldPropagateError_WhenMenuNotFound() {
        // given
        Long menuId = 999L;
        Long storeId = 1L;
        String userId = "user123";
        Menu updatedMenuData = createTestMenu(null, storeId, "Update Attempt", null);
        // 이미지가 있든 없든 테스트 가능. @Nullable 어노테이션은 제거합니다.
        FilePart newImageFile = null; // <-- @Nullable 어노테이션 제거

        // findById 호출 시 빈 Mono 반환 Mocking (메뉴 없음)
        when(menuRepository.findById(menuId)).thenReturn(Mono.empty());

        // when
        Mono<Menu> resultMono = menuService.updateMenuWithImage(menuId, updatedMenuData, newImageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectError(NoSuchElementException.class)
                .verify();

        verify(menuRepository).findById(menuId);
        verify(menuRepository, never()).save(any(Menu.class));
    }
    @Test
    void updateMenuWithImage_ShouldPropagateError_WhenRepositorySaveErrors() {
        // given
        Long menuId = 1L;
        Long storeId = 1L;
        String userId = "user123";
        Menu existingMenu = createTestMenu(menuId, storeId, "Original Name", "http://example.com/old.jpg");
        Menu updatedMenuData = createTestMenu(null, storeId, "Updated Name", null);
        FilePart newImageFile = null;
        RuntimeException repositoryError = new RuntimeException("DB update failed");

        // findById 호출 시 기존 메뉴 반환 Mocking
        when(menuRepository.findById(menuId)).thenReturn(Mono.just(existingMenu));
        // save 호출 시 오류 발행하도록 Mocking
        when(menuRepository.save(any(Menu.class))).thenReturn(Mono.error(repositoryError));

        // when
        Mono<Menu> resultMono = menuService.updateMenuWithImage(menuId, updatedMenuData, newImageFile, userId);

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("DB update failed"))
                .verify();

        verify(menuRepository).findById(menuId);
        verify(menuRepository).save(any(Menu.class));
    }

    // --- deleteMenu 테스트 ---
    @Test
    void deleteMenu_ShouldDeleteMenu_WhenFound() {
        // given
        Long menuId = 1L;
        // existsById 호출 시 true 반환 Mocking (메뉴 존재함)
        when(menuRepository.existsById(menuId)).thenReturn(Mono.just(true));
        // deleteById 호출 시 완료되는 Mono<Void> 반환 Mocking (삭제 성공)
        when(menuRepository.deleteById(menuId)).thenReturn(Mono.empty());

        // when
        Mono<Void> resultMono = menuService.deleteMenu(menuId);

        // then
        StepVerifier.create(resultMono)
                .verifyComplete(); // 스트림이 성공적으로 완료되는지 검증 (Mono<Void>의 경우)

        // existsById와 deleteById 메서드가 올바른 ID로 호출되었는지 확인
        verify(menuRepository).existsById(menuId);
        verify(menuRepository).deleteById(menuId);
    }

    @Test
    void deleteMenu_ShouldThrowNoSuchElementException_WhenNotFound() {
        // given
        Long menuId = 999L;
        // existsById 호출 시 false 반환 Mocking (메뉴 없음)
        when(menuRepository.existsById(menuId)).thenReturn(Mono.just(false));

        // when
        Mono<Void> resultMono = menuService.deleteMenu(menuId);

        // then
        StepVerifier.create(resultMono)
                .expectError(NoSuchElementException.class) // NoSuchElementException 예외가 발행되는지 검증
                .verify();

        // existsById는 호출되지만, deleteById는 호출되지 않아야 함
        verify(menuRepository).existsById(menuId);
        verify(menuRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMenu_ShouldPropagateError_WhenExistsByIdRepositoryErrors() {
        // given
        Long menuId = 1L;
        RuntimeException repositoryError = new RuntimeException("Exists check failed");
        // existsById 호출 시 오류 발행하도록 Mocking
        when(menuRepository.existsById(menuId)).thenReturn(Mono.error(repositoryError));

        // when
        Mono<Void> resultMono = menuService.deleteMenu(menuId);

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Exists check failed"))
                .verify();

        verify(menuRepository).existsById(menuId);
        verify(menuRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMenu_ShouldPropagateError_WhenDeleteByIdRepositoryErrors() {
        // given
        Long menuId = 1L;
        RuntimeException repositoryError = new RuntimeException("Deletion failed in DB");
        // existsById 호출 시 true 반환 Mocking
        when(menuRepository.existsById(menuId)).thenReturn(Mono.just(true));
        // deleteById 호출 시 오류 발행하도록 Mocking
        when(menuRepository.deleteById(menuId)).thenReturn(Mono.error(repositoryError));

        // when
        Mono<Void> resultMono = menuService.deleteMenu(menuId);

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Deletion failed in DB"))
                .verify();

        verify(menuRepository).existsById(menuId);
        verify(menuRepository).deleteById(menuId);
    }


    // --- findAllMenus 테스트 ---
    @Test
    void findAllMenus_ShouldReturnAllMenus() {
        // given
        Menu menu1 = createTestMenu(1L, 1L, "Menu A", "urlA");
        Menu menu2 = createTestMenu(2L, 1L, "Menu B", "urlB");
        List<Menu> mockMenus = Arrays.asList(menu1, menu2);
        // findAll 호출 시 Flux<Menu> 반환 Mocking
        when(menuRepository.findAll()).thenReturn(Flux.fromIterable(mockMenus));

        // when
        Flux<Menu> resultFlux = menuService.findAllMenus();

        // then
        StepVerifier.create(resultFlux)
                .expectNext(menu1, menu2)
                .verifyComplete();

        verify(menuRepository).findAll();
    }

    @Test
    void findAllMenus_ShouldReturnEmptyFlux_WhenNoMenusExist() {
        // given
        // findAll 호출 시 빈 Flux 반환 Mocking
        when(menuRepository.findAll()).thenReturn(Flux.empty());

        // when
        Flux<Menu> resultFlux = menuService.findAllMenus();

        // then
        StepVerifier.create(resultFlux)
                .expectNextCount(0)
                .verifyComplete();

        verify(menuRepository).findAll();
    }

    @Test
    void findAllMenus_ShouldPropagateError_WhenRepositoryErrors() {
        // given
        RuntimeException repositoryError = new RuntimeException("Find all failed");
        // findAll 호출 시 오류 발행 Mocking
        when(menuRepository.findAll()).thenReturn(Flux.error(repositoryError));

        // when
        Flux<Menu> resultFlux = menuService.findAllMenus();

        // then
        StepVerifier.create(resultFlux)
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Find all failed"))
                .verify();

        verify(menuRepository).findAll();
    }
}