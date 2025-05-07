package com.localy.cart_service.cart.controller;

import com.localy.cart_service.cart.domain.Cart;
import com.localy.cart_service.cart.dto.AddItemRequest;
import com.localy.cart_service.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<String> addItem(@RequestHeader("X-User-Id") String userId,
                                          @RequestBody AddItemRequest requestBody) { // 요청 파라미터로 가게 ID 받기
        try {
            cartService.addItem(userId,
                    requestBody.getMenuId(),
                    requestBody.getMenuName(),
                    requestBody.getQuantity(),
                    requestBody.getUnitPrice(),
                    requestBody.getStoreId());
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("")
    public ResponseEntity<Cart> getCart(@RequestHeader("X-User-Id") String userId) {
        Cart cart = cartService.getCart(userId);
        if (cart != null) {
            return new ResponseEntity<>(cart, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/items/{menuId}")
    public ResponseEntity<Void> updateQuantity(@RequestHeader("X-User-Id") String userId,
                                               @PathVariable String menuId,
                                               @RequestParam Integer quantity) {
        cartService.updateQuantity(userId, menuId, quantity);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> removeItem(@RequestHeader("X-User-Id") String userId,
                                           @RequestParam String menuId) {
        cartService.removeItem(userId, menuId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("")
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Id") String userId) {
        cartService.clearCart(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/total")
    public ResponseEntity<BigDecimal> calculateTotal(@RequestHeader("X-User-Id") String userId) {
        BigDecimal total = cartService.calculateTotal(userId);
        return new ResponseEntity<>(total, HttpStatus.OK);
    }
}