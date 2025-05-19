// 파일 위치: lib/data/models/cart_models.dart
import 'package:flutter/foundation.dart';

// 장바구니에 아이템 추가 요청 시 사용될 데이터 모델
@immutable
class AddItemRequest {
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice; // BigDecimal은 Dart에서 double로 표현
  final String storeId;   // 어느 가게의 메뉴인지 식별

  const AddItemRequest({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
    required this.storeId,
  });

  Map<String, dynamic> toJson() {
    return {
      'menuId': menuId,
      'menuName': menuName,
      'quantity': quantity,
      'unitPrice': unitPrice,
      'storeId': storeId,
    };
  }
}

// 장바구니 내 개별 아이템을 나타내는 데이터 모델
@immutable
class CartItem {
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice;

  const CartItem({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
  });

  factory CartItem.fromJson(Map<String, dynamic> json) {
    return CartItem(
      menuId: json['menuId'] as String? ?? '',
      menuName: json['menuName'] as String? ?? '이름 없는 상품',
      quantity: json['quantity'] as int? ?? 0,
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'menuId': menuId,
      'menuName': menuName,
      'quantity': quantity,
      'unitPrice': unitPrice,
    };
  }

  // 아이템별 합계 금액 계산 (선택적 편의 메서드)
  double get totalPrice => unitPrice * quantity;
}

// 장바구니 전체를 나타내는 데이터 모델
@immutable
class Cart {
  final String userId; // 장바구니 소유자 ID
  final Map<String, CartItem> cartItems; // Key: menuId, Value: CartItem 객체
  final String? storeId; // 현재 장바구니에 담긴 아이템들이 속한 가게 ID (하나의 장바구니는 하나의 가게 상품만)

  const Cart({
    required this.userId,
    required this.cartItems,
    this.storeId,
  });

  factory Cart.fromJson(Map<String, dynamic> json) {
    return Cart(
      userId: json['userId'] as String? ?? '',
      cartItems: (json['cartItems'] as Map<String, dynamic>?)?.map(
            (key, value) => MapEntry(key, CartItem.fromJson(value as Map<String, dynamic>)),
      ) ?? {}, // cartItems가 null일 경우 빈 맵으로 초기화
      storeId: json['storeId'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'cartItems': cartItems.map((key, value) => MapEntry(key, value.toJson())),
      'storeId': storeId,
    };
  }

  // 장바구니 총액 계산 (선택적 편의 메서드)
  double get totalCartPrice {
    if (cartItems.isEmpty) return 0.0;
    return cartItems.values.fold(0.0, (sum, item) => sum + item.totalPrice);
  }
}

// 체크아웃(주문 요청) 결과를 담는 데이터 모델
// 백엔드의 CheckoutResult DTO와 대응
@immutable
class CheckoutResult {
  final bool success;       // 주문 요청 성공 여부
  final String? orderId;    // 성공 시 주문 ID (실패 시 null)
  final String? errorMessage; // 실패 시 오류 메시지 (성공 시 null)

  const CheckoutResult({
    required this.success,
    this.orderId,
    this.errorMessage,
  });
}
