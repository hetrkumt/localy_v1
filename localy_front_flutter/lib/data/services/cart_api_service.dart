// 파일 위치: lib/data/services/cart_api_service.dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:localy_front_flutter/data/models/cart_models.dart';
import 'package:localy_front_flutter/data/services/api_client.dart';

class CartApiService {
  final ApiClient _apiClient;

  CartApiService({required ApiClient apiClient}) : _apiClient = apiClient;

  Future<Cart> getCart() async {
    debugPrint("CartApiService: getCart 호출");
    try {
      final response = await _apiClient.get('/carts');
      if (response.statusCode == 200) {
        final responseData = json.decode(utf8.decode(response.bodyBytes));
        // 백엔드 CartService.getCart가 빈 Cart 객체를 반환하므로, userId는 여기서 채워져야 함.
        // 또는 AuthProvider에서 userId를 가져와 Cart.fromJson에 전달해야 함.
        // 현재 Cart.fromJson은 userId를 필수로 요구.
        // 이 부분은 백엔드 응답에 userId가 포함되어 있거나,
        // Cart.fromJson에서 userId를 선택적으로 받도록 수정 필요.
        // 임시로 백엔드가 userId를 보내준다고 가정.
        return Cart.fromJson(responseData);
      } else if (response.statusCode == 404) {
        // 백엔드가 빈 Cart를 반환하도록 수정했으므로, 404는 예외적인 상황.
        debugPrint('CartApiService: Cart not found (404), an issue might exist.');
        throw Exception('장바구니를 찾을 수 없습니다 (404).');
      } else {
        debugPrint('CartApiService: Failed to get cart - Status: ${response.statusCode}, Body: ${response.body}');
        throw Exception('장바구니 정보를 가져오는데 실패했습니다: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('CartApiService: getCart 중 예외 발생 - $e');
      rethrow;
    }
  }

  Future<Cart> addItemToCart(AddItemToCartRequest itemRequest) async {
    debugPrint("CartApiService: addItemToCart 호출 - MenuId: ${itemRequest.menuId}, Qty: ${itemRequest.quantity}");
    try {
      final response = await _apiClient.post(
        '/carts/items',
        itemRequest.toJson(),
      );

      if (response.statusCode == 201 || response.statusCode == 200) { // 201 Created 또는 200 OK (업데이트된 Cart 반환 시)
        debugPrint('CartApiService: Item added/updated in cart successfully. Response body: ${response.body}');
        return Cart.fromJson(json.decode(utf8.decode(response.bodyBytes)));
      } else {
        debugPrint('CartApiService: Failed to add item to cart - Status: ${response.statusCode}, Body: ${response.body}');
        String errorMessage = "상품을 장바구니에 추가하는데 실패했습니다.";
        try {
          if (response.headers['content-type']?.contains('text/plain') ?? false) {
            errorMessage = utf8.decode(response.bodyBytes);
          } else {
            final errorBody = json.decode(utf8.decode(response.bodyBytes));
            if (errorBody['message'] != null) errorMessage = errorBody['message'];
          }
        } catch (_) {}
        throw Exception(errorMessage);
      }
    } catch (e) {
      debugPrint('CartApiService: addItemToCart 중 예외 발생 - $e');
      rethrow;
    }
  }

  Future<Cart> updateItemQuantity(String menuId, int quantity) async {
    debugPrint("CartApiService: updateItemQuantity 호출 - MenuId: $menuId, New Qty: $quantity");
    try {
      final response = await _apiClient.put(
        '/carts/items/$menuId?quantity=$quantity', // 백엔드 API 경로 및 파라미터 형식에 맞춤
        {}, // PUT 요청 본문은 비어있음 (쿼리 파라미터 사용)
      );

      if (response.statusCode == 200) {
        debugPrint('CartApiService: Item quantity updated successfully. Response body: ${response.body}');
        return Cart.fromJson(json.decode(utf8.decode(response.bodyBytes)));
      } else {
        debugPrint('CartApiService: Failed to update item quantity - Status: ${response.statusCode}, Body: ${response.body}');
        throw Exception('상품 수량 변경에 실패했습니다: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('CartApiService: updateItemQuantity 중 예외 발생 - $e');
      rethrow;
    }
  }

  Future<Cart> removeItemFromCart(String menuId) async {
    debugPrint("CartApiService: removeItemFromCart 호출 - MenuId: $menuId");
    try {
      final response = await _apiClient.delete(
        '/carts/items?menuId=$menuId', // 백엔드 API 경로 및 파라미터 형식에 맞춤
      );

      if (response.statusCode == 200) { // 백엔드가 OK와 함께 업데이트된 Cart 반환
        debugPrint('CartApiService: Item removed from cart successfully. Response body: ${response.body}');
        return Cart.fromJson(json.decode(utf8.decode(response.bodyBytes)));
      } else if (response.statusCode == 204) { // 성공, 내용 없음 (이 경우는 거의 없을 것, 보통 200 OK와 함께 Cart 반환)
        debugPrint('CartApiService: Item removed (204), fetching cart again.');
        return getCart(); // 204면 최신 Cart를 다시 가져옴
      }
      else {
        debugPrint('CartApiService: Failed to remove item from cart - Status: ${response.statusCode}, Body: ${response.body}');
        throw Exception('상품 삭제에 실패했습니다: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('CartApiService: removeItemFromCart 중 예외 발생 - $e');
      rethrow;
    }
  }

  Future<Cart> clearCart() async {
    debugPrint("CartApiService: clearCart 호출");
    try {
      final response = await _apiClient.delete('/carts');
      if (response.statusCode == 200) { // 백엔드가 OK와 함께 비워진 Cart 반환
        debugPrint('CartApiService: Cart cleared successfully. Response body: ${response.body}');
        return Cart.fromJson(json.decode(utf8.decode(response.bodyBytes)));
      } else if (response.statusCode == 204) {
        debugPrint('CartApiService: Cart cleared (204), fetching cart again (should be empty).');
        return getCart(); // 204면 최신 Cart를 다시 가져옴 (빈 Cart가 될 것임)
      }
      else {
        debugPrint('CartApiService: Failed to clear cart - Status: ${response.statusCode}, Body: ${response.body}');
        throw Exception('장바구니 비우기에 실패했습니다: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('CartApiService: clearCart 중 예외 발생 - $e');
      rethrow;
    }
  }
}
