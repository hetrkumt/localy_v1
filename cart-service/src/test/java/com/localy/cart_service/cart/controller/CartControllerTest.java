package com.localy.cart_service.cart.controller; // 실제 컨트롤러 패키지 경로에 맞게 수정

import com.localy.cart_service.cart.domain.Cart; // Cart 도메인 클래스 import (경로 확인)
import com.localy.cart_service.cart.domain.CartItem; // CartItem 도메인 클래스 import (경로 확인)
import com.localy.cart_service.cart.dto.AddItemRequest; // AddItemRequest DTO import (경로 확인)
import com.localy.cart_service.cart.service.CartService; // CartService import (경로 확인)

import com.fasterxml.jackson.databind.ObjectMapper; // Jackson ObjectMapper import
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType; // Media Type import
import org.springframework.test.web.servlet.MockMvc; // MockMvc import

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*; // Mockito Argument Matchers import
import static org.mockito.Mockito.*; // Mockito main methods import
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; // MockMvc Builders import
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // MockMvc Matchers import

// Spring Boot 테스트 컨텍스트 로드
@SpringBootTest
// MockMvc 자동 설정
@AutoConfigureMockMvc
class CartControllerTest {

    // MockMvc 주입 (HTTP 요청 시뮬레이션)
    @Autowired
    private MockMvc mockMvc;

    // CartService Mock 객체 주입 (실제 서비스 대신 사용)
    @MockBean
    private CartService cartService;

    // JSON 변환을 위한 ObjectMapper 주입
    @Autowired
    private ObjectMapper objectMapper;

    private final String USER_ID = "testUser";
    private final String BASE_URL = "/api/carts";

    // POST /api/carts/items 테스트
    @Test
    @DisplayName("장바구니 상품 추가 성공")
    void addItem_Success() throws Exception {
        // 테스트용 AddItemRequest 객체 생성
        AddItemRequest addItemRequest = new AddItemRequest();
        addItemRequest.setMenuId("menu1");
        addItemRequest.setMenuName("Test Menu");
        addItemRequest.setQuantity(1);
        addItemRequest.setUnitPrice(BigDecimal.valueOf(1000));
        addItemRequest.setStoreId("store1");

        // cartService.addItem 메서드가 호출될 때 아무것도 하지 않도록 Mock 설정
        // (void 메서드는 doNothing() 사용)
        doNothing().when(cartService).addItem(
                eq(USER_ID), // 특정 값으로 호출되는지 검증
                eq(addItemRequest.getMenuId()),
                eq(addItemRequest.getMenuName()),
                eq(addItemRequest.getQuantity()),
                eq(addItemRequest.getUnitPrice()),
                eq(addItemRequest.getStoreId())
        );

        // MockMvc를 사용하여 POST 요청 시뮬레이션
        mockMvc.perform(post(BASE_URL + "/items") // POST /api/carts/items 엔드포인트
                        .header("X-User-Id", USER_ID) // X-User-Id 헤더 추가
                        .contentType(MediaType.APPLICATION_JSON) // Content-Type 설정
                        .content(objectMapper.writeValueAsString(addItemRequest))) // 요청 본문 (DTO -> JSON 변환)
                // 응답 상태 코드가 201 (Created)인지 검증
                .andExpect(status().isCreated());

        // cartService.addItem 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).addItem(
                eq(USER_ID),
                eq(addItemRequest.getMenuId()),
                eq(addItemRequest.getMenuName()),
                eq(addItemRequest.getQuantity()),
                eq(addItemRequest.getUnitPrice()),
                eq(addItemRequest.getStoreId())
        );
    }

    // POST /api/carts/items 테스트 (IllegalStateException 발생 시)
    @Test
    @DisplayName("장바구니 상품 추가 실패 (IllegalStateException)")
    void addItem_IllegalStateException() throws Exception {
        AddItemRequest addItemRequest = new AddItemRequest();
        addItemRequest.setMenuId("menu1");
        addItemRequest.setMenuName("Test Menu");
        addItemRequest.setQuantity(1);
        addItemRequest.setUnitPrice(BigDecimal.valueOf(1000));
        addItemRequest.setStoreId("store1");

        String errorMessage = "장바구니에는 동일한 가게의 상품만 담을 수 있습니다.";

        // cartService.addItem 메서드 호출 시 IllegalStateException을 발생시키도록 Mock 설정
        doThrow(new IllegalStateException(errorMessage)).when(cartService).addItem(
                eq(USER_ID),
                anyString(), // 어떤 문자열이 와도
                anyString(),
                anyInt(), // 어떤 정수가 와도
                any(BigDecimal.class), // 어떤 BigDecimal이 와도
                anyString()
        );

        // MockMvc를 사용하여 POST 요청 시뮬레이션
        mockMvc.perform(post(BASE_URL + "/items")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                // 응답 상태 코드가 400 (Bad Request)인지 검증
                .andExpect(status().isBadRequest())
                // 응답 본문이 예상된 에러 메시지인지 검증
                .andExpect(content().string(errorMessage));

        // cartService.addItem 메서드가 한 번 호출되었는지 검증
        verify(cartService, times(1)).addItem(
                eq(USER_ID),
                anyString(),
                anyString(),
                anyInt(),
                any(BigDecimal.class),
                anyString()
        );
    }


    // GET /api/carts 테스트
    @Test
    @DisplayName("장바구니 조회 성공")
    void getCart_Found() throws Exception {
        // 테스트용 Cart 객체 생성
        Cart mockCart = new Cart();
        mockCart.setUserId(USER_ID);
        mockCart.setStoreId("store1");
        mockCart.setCartItems(new HashMap<>()); // 빈 맵으로 초기화

        // cartService.getCart 호출 시 mockCart 객체를 반환하도록 Mock 설정
        when(cartService.getCart(eq(USER_ID))).thenReturn(mockCart);

        // MockMvc를 사용하여 GET 요청 시뮬레이션
        mockMvc.perform(get(BASE_URL) // GET /api/carts 엔드포인트
                        .header("X-User-Id", USER_ID)) // X-User-Id 헤더 추가
                // 응답 상태 코드가 200 (OK)인지 검증
                .andExpect(status().isOk())
                // 응답 본문이 JSON 형태이고, userId 필드가 예상 값인지 검증
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Content-Type이 JSON인지
                .andExpect(jsonPath("$.userId").value(USER_ID)) // JSON 응답 본문의 userId 필드 검증
                .andExpect(jsonPath("$.storeId").value("store1")); // JSON 응답 본문의 storeId 필드 검증
        // .andExpect(jsonPath("$.cartItems").isEmpty()); // 필요하다면 cartItems 맵이 비어있는지 검증

        // cartService.getCart 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).getCart(eq(USER_ID));
    }

    // GET /api/carts 테스트 (장바구니 없을 경우)
    @Test
    @DisplayName("장바구니 조회 실패 (없음)")
    void getCart_NotFound() throws Exception {
        // cartService.getCart 호출 시 null을 반환하도록 Mock 설정 (장바구니가 없는 경우)
        when(cartService.getCart(eq(USER_ID))).thenReturn(null);

        // MockMvc를 사용하여 GET 요청 시뮬레이션
        mockMvc.perform(get(BASE_URL)
                        .header("X-User-Id", USER_ID))
                // 응답 상태 코드가 404 (Not Found)인지 검증
                .andExpect(status().isNotFound());

        // cartService.getCart 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).getCart(eq(USER_ID));
    }

    // PUT /api/carts/items/{menuId} 테스트
    @Test
    @DisplayName("장바구니 상품 수량 업데이트 성공")
    void updateQuantity_Success() throws Exception {
        String menuId = "menu1";
        Integer quantity = 5;

        // cartService.updateQuantity 호출 시 아무것도 하지 않도록 Mock 설정
        doNothing().when(cartService).updateQuantity(eq(USER_ID), eq(menuId), eq(quantity));

        // MockMvc를 사용하여 PUT 요청 시뮬레이션
        mockMvc.perform(put(BASE_URL + "/items/{menuId}", menuId) // PUT /api/carts/items/{menuId} 엔드포인트 (PathVariable 사용)
                        .header("X-User-Id", USER_ID)
                        .param("quantity", quantity.toString())) // RequestParam "quantity" 추가
                // 응답 상태 코드가 200 (OK)인지 검증
                .andExpect(status().isOk());

        // cartService.updateQuantity 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).updateQuantity(eq(USER_ID), eq(menuId), eq(quantity));
    }

    // DELETE /api/carts/items 테스트
    @Test
    @DisplayName("장바구니 특정 상품 삭제 성공")
    void removeItem_Success() throws Exception {
        String menuId = "menu1";

        // cartService.removeItem 호출 시 아무것도 하지 않도록 Mock 설정
        doNothing().when(cartService).removeItem(eq(USER_ID), eq(menuId));

        // MockMvc를 사용하여 DELETE 요청 시뮬레이션
        mockMvc.perform(delete(BASE_URL + "/items") // DELETE /api/carts/items 엔드포인트
                        .header("X-User-Id", USER_ID)
                        .param("menuId", menuId)) // RequestParam "menuId" 추가
                // 응답 상태 코드가 204 (No Content)인지 검증
                .andExpect(status().isNoContent());

        // cartService.removeItem 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).removeItem(eq(USER_ID), eq(menuId));
    }

    // DELETE /api/carts 테스트
    @Test
    @DisplayName("장바구니 전체 비우기 성공")
    void clearCart_Success() throws Exception {
        // cartService.clearCart 호출 시 아무것도 하지 않도록 Mock 설정
        doNothing().when(cartService).clearCart(eq(USER_ID));

        // MockMvc를 사용하여 DELETE 요청 시뮬레이션
        mockMvc.perform(delete(BASE_URL) // DELETE /api/carts 엔드포인트
                        .header("X-User-Id", USER_ID))
                // 응답 상태 코드가 204 (No Content)인지 검증
                .andExpect(status().isNoContent());

        // cartService.clearCart 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).clearCart(eq(USER_ID));
    }

    // GET /api/carts/total 테스트
    @Test
    @DisplayName("장바구니 총 금액 계산 성공")
    void calculateTotal_Success() throws Exception {
        BigDecimal totalAmount = BigDecimal.valueOf(25500); // 예상 총 금액

        // cartService.calculateTotal 호출 시 예상 총 금액을 반환하도록 Mock 설정
        when(cartService.calculateTotal(eq(USER_ID))).thenReturn(totalAmount);

        // MockMvc를 사용하여 GET 요청 시뮬레이션
        mockMvc.perform(get(BASE_URL + "/total") // GET /api/carts/total 엔드포인트
                        .header("X-User-Id", USER_ID))
                // 응답 상태 코드가 200 (OK)인지 검증
                .andExpect(status().isOk())
                // 응답 본문이 예상 총 금액과 일치하는지 검증
                .andExpect(content().string(totalAmount.toString())); // BigDecimal은 문자열로 비교

        // cartService.calculateTotal 메서드가 예상된 인자들로 정확히 한 번 호출되었는지 검증
        verify(cartService, times(1)).calculateTotal(eq(USER_ID));
    }
}