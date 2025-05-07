package com.localy.cart_service.cart.service;

import com.localy.cart_service.cart.domain.Cart;
import com.localy.cart_service.cart.domain.CartItem;
import com.localy.cart_service.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;

    public void addItem(String userId, String menuId, String menuName, Integer quantity, BigDecimal unitPrice, String storeIdOfItem) {
        // 사용자 ID로 장바구니를 찾거나 새로 생성
        Cart cart = cartRepository.findById(userId)
                .orElseGet(() -> { // findById 결과가 비어있을 때만 이 람다 실행
                    Cart newCart = new Cart();
                    newCart.setUserId(userId); // <-- 새로 생성된 Cart 객체에 userId 설정!
                    // Note: 여기서 cartItems 맵도 초기화하는 것이 좋습니다.
                    //newCart.setCartItems(new HashMap<>()); // <-- 이전 NullPointerException 방지 코드와 합치는 것이 좋음
                    return newCart;
                });

        if (cart.getCartItems() == null) {
            System.out.println("CartService: 로딩된 기존 장바구니의 cartItems 맵이 null이어서 초기화합니다. 사용자 ID: " + userId);
            cart.setCartItems(new HashMap<>()); // <-- null일 경우 새로운 HashMap으로 초기화
        }

        // 장바구니 아이템 맵을 가져오거나 새로 생성
        Map<String, CartItem> cartItems = cart.getCartItems();

        // 비어있는 장바구니에 첫 상품을 담는 경우: 가게 ID 설정
        // cartItems.isEmpty() 호출 안전
        if (cartItems.isEmpty()) {
            if (cart.getStoreId() != null && !cart.getStoreId().equals(storeIdOfItem)) {
                // 이 경우는 이론적으로 발생하기 어렵지만, 데이터 불일치 상황 대비
                throw new IllegalStateException("장바구니에는 동일한 가게의 상품만 담을 수 있습니다 (초기 상태 불일치).");
            }
            cart.setStoreId(storeIdOfItem);
        }
        // 비어있지 않은 장바구니에 상품을 추가하는 경우: 기존 가게 ID와 비교
        else {
            if (cart.getStoreId() != null && !cart.getStoreId().equals(storeIdOfItem)) {
                throw new IllegalStateException("장바구니에는 동일한 가게의 상품만 담을 수 있습니다.");
            }
            // Note: cartItems가 비어있지 않은데 storeId가 null인 경우는 데이터 문제일 수 있음
            // 로직에 따라 여기서 storeIdOfItem을 설정할 수도 있습니다. (현재 로직은 예외 발생 또는 그냥 통과)
        }

        // 이미 장바구니에 해당 상품이 있는 경우 수량 증가
        CartItem existingItem = cartItems.get(menuId);
        if (existingItem != null) {
            // 기존 아이템이 있으면 수량과 가격 업데이트
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            // TODO: total price 업데이트 로직 추가 (단가 * 새로운 수량)
            // existingItem.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(existingItem.getQuantity())));
            System.out.println("CartService: 기존 상품 수량 업데이트 - 사용자 ID: " + userId + ", 메뉴 ID: " + menuId + ", 새로운 수량: " + existingItem.getQuantity());
        } else {
            // 새로운 아이템이면 CartItem 객체 생성 및 맵에 추가
            CartItem newItem = CartItem.builder()
                    .menuId(menuId)
                    .menuName(menuName)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    // TODO: total price 계산 로직 추가 (단가 * 수량)
                    // .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                    .build();
            cartItems.put(menuId, newItem); // 맵에 추가
            System.out.println("CartService: 새로운 상품 추가 - 사용자 ID: " + userId + ", 메뉴 ID: " + menuId + ", 수량: " + quantity);
        }

        cart.setCartItems(cartItems);

        // Redis에 장바구니 저장
        cartRepository.save(cart);
        System.out.println("CartService: 장바구니 저장 완료 - 사용자 ID: " + userId + ", 담긴 상품 수: " + cartItems.size()); // <-- 저장 완료 로그 추가
        // ======================================================
    }

    // 장바구니 조회
    public Cart getCart(String userId) {
        return cartRepository.findById(userId).orElse(null);
    }

    //수량 변경
    public void updateQuantity(String userId, String menuId, Integer quantity) {
        Cart cart = cartRepository.findById(userId).orElse(null);
        if (cart != null) {
            Map<String, CartItem> cartItems = cart.getCartItems();
            if (cartItems != null && cartItems.containsKey(menuId)) {
                CartItem existingItem = cartItems.get(menuId);
                existingItem.setQuantity(quantity);
                cartRepository.save(cart);
            }
        }
    }

    //상품 삭제
    public void removeItem(String userId, String menuId) {
        Cart cart = cartRepository.findById(userId).orElse(null);
        if (cart != null) {
            Map<String, CartItem> cartItems = cart.getCartItems();
            if (cartItems != null && cartItems.containsKey(menuId)) {
                cartItems.remove(menuId);
                cartRepository.save(cart);
            }
        }
    }

    // 장바구니 비우기
    public void clearCart(String userId) {
        Cart cart = cartRepository.findById(userId).orElse(null);
        if (cart != null) {
            cart.getCartItems().clear();
            cartRepository.save(cart);
        }
    }

    // 총 금액 계산
    public BigDecimal calculateTotal(String userId) {
        Cart cart = cartRepository.findById(userId).orElse(null);
        BigDecimal total = BigDecimal.ZERO;
        if (cart != null && cart.getCartItems() != null) {
            for (CartItem item : cart.getCartItems().values()) {
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(itemTotal);
            }
        }
        return total;
    }
}