// 파일 위치: lib/data/services/cart_api_service.dart
import 'dart:convert'; // utf8 사용을 위해 추가
import 'package:flutter/foundation.dart';
import '../models/cart_models.dart'; // 장바구니 관련 데이터 모델 import
import 'api_client.dart';           // 공통 API 클라이언트 import

class CartApiService {
  final ApiClient _apiClient;

  CartApiService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  Future<void> addItemToCart(AddItemRequest itemRequest) async {
    try {
      final response = await _apiClient.post('/carts/items', itemRequest.toJson());
      _apiClient.processResponse(response);
      debugPrint("CartApiService: 아이템 장바구니 추가 성공.");
    } catch (e) {
      debugPrint("CartApiService: 아이템 장바구니 추가 실패 - $e");
      if (e.toString().contains("다른 가게의 상품")) {
        throw Exception("장바구니에는 한 가게의 상품만 담을 수 있습니다.");
      }
      throw Exception("장바구니에 상품을 추가하는데 실패했습니다.");
    }
  }

  Future<Cart> getCart() async {
    try {
      final response = await _apiClient.get('/carts');
      final dynamic responseData = _apiClient.processResponse(response, expectFullJsonResponse: true); // JSON 객체 기대
      if (responseData == null) {
        // 204 No Content 또는 빈 응답 처리 (예: 장바구니가 실제로 비어있을 때)
        // 이 경우는 백엔드가 빈 Cart 객체 대신 204를 반환할 때 발생 가능
        // 또는 processResponse에서 null을 반환했을 때
        debugPrint("CartApiService: 장바구니가 비어있거나 없습니다 (응답 본문 없음 또는 null).");
        // 현재 사용자 ID를 가져오는 로직이 필요 (예: AuthService 또는 토큰에서 직접 추출)
        // String currentUserId = await _authService.getCurrentUserId(); // 가상의 메서드
        // 여기서는 임시로 'unknown_user' 사용
        return const Cart(userId: 'unknown_user_placeholder', cartItems: {}, storeId: null);
      }
      debugPrint("CartApiService: 장바구니 조회 성공.");
      return Cart.fromJson(responseData as Map<String, dynamic>);
    } catch (e) {
      debugPrint("CartApiService: 장바구니 조회 실패 - $e");
      if (e.toString().contains("404") || e.toString().contains("찾을 수 없습니다")) {
        // 404는 장바구니가 없는 경우일 수 있음. 빈 장바구니로 처리.
        // String currentUserId = await _authService.getCurrentUserId();
        return const Cart(userId: 'unknown_user_placeholder', cartItems: {}, storeId: null);
      }
      throw Exception("장바구니 정보를 가져오는데 실패했습니다.");
    }
  }

  Future<void> updateItemQuantity(String menuId, int quantity) async {
    try {
      final response = await _apiClient.put('/carts/items/$menuId?quantity=$quantity', {});
      _apiClient.processResponse(response);
      debugPrint("CartApiService: 아이템 수량 변경 성공 (MenuID: $menuId, Quantity: $quantity).");
    } catch (e) {
      debugPrint("CartApiService: 아이템 수량 변경 실패 - $e");
      throw Exception("상품 수량 변경에 실패했습니다.");
    }
  }

  Future<void> removeItemFromCart(String menuId) async {
    try {
      final response = await _apiClient.delete('/carts/items?menuId=$menuId');
      _apiClient.processResponse(response);
      debugPrint("CartApiService: 아이템 삭제 성공 (MenuID: $menuId).");
    } catch (e) {
      debugPrint("CartApiService: 아이템 삭제 실패 - $e");
      throw Exception("장바구니에서 상품을 삭제하는데 실패했습니다.");
    }
  }

  Future<void> clearCart() async {
    try {
      final response = await _apiClient.delete('/carts');
      _apiClient.processResponse(response);
      debugPrint("CartApiService: 장바구니 비우기 성공.");
    } catch (e) {
      debugPrint("CartApiService: 장바구니 비우기 실패 - $e");
      throw Exception("장바구니를 비우는데 실패했습니다.");
    }
  }

  Future<double> calculateTotal() async {
    try {
      final response = await _apiClient.get('/carts/total');
      // 백엔드에서 BigDecimal을 문자열로 반환한다고 가정
      final String responseBody = _apiClient.processResponse(response) as String; // expectFullJsonResponse 기본값 false
      debugPrint("CartApiService: 장바구니 총액 계산 성공 - $responseBody");
      return double.tryParse(responseBody) ?? 0.0;
    } catch (e) {
      debugPrint("CartApiService: 장바구니 총액 계산 실패 - $e");
      return 0.0; // 오류 시 0.0 반환 또는 예외 발생
    }
  }

  Future<CheckoutResult> checkout() async {
    try {
      final response = await _apiClient.post('/carts/checkout', {});
      // 성공(200 OK) 시 주문 ID(문자열), 실패(400, 500 등) 시 오류 메시지(문자열) 반환
      if (response.statusCode >= 200 && response.statusCode < 300 ) {
        // 성공 시 응답 본문은 주문 ID 문자열
        final String orderId = utf8.decode(response.bodyBytes);
        debugPrint("CartApiService: 체크아웃 성공, 주문 ID: $orderId");
        return CheckoutResult(success: true, orderId: orderId);
      } else {
        // 실패 시 응답 본문은 오류 메시지 문자열
        final String errorMessage = utf8.decode(response.bodyBytes);
        debugPrint("CartApiService: 체크아웃 실패 - ${response.statusCode}: $errorMessage");
        return CheckoutResult(success: false, errorMessage: errorMessage);
      }
    } catch (e) {
      debugPrint("CartApiService: 체크아웃 중 예외 발생 - $e");
      return CheckoutResult(success: false, errorMessage: e.toString().replaceAll("Exception: ", ""));
    }
  }
}
