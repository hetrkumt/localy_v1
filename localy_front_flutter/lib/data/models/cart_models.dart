// 파일 위치: lib/data/models/cart_models.dart
import 'package:flutter/foundation.dart';

// CartItem 모델 (변경 없음)
class CartItem {
  final String menuId;
  final String menuName;
  int quantity;
  final double unitPrice;

  CartItem({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
  });

  double get totalPrice => unitPrice * quantity;

  Map<String, dynamic> toJson() => {
    'menuId': menuId,
    'menuName': menuName,
    'quantity': quantity,
    'unitPrice': unitPrice,
  };

  factory CartItem.fromJson(Map<String, dynamic> json) {
    return CartItem(
      menuId: json['menuId'] as String,
      menuName: json['menuName'] as String,
      quantity: json['quantity'] as int,
      unitPrice: (json['unitPrice'] as num).toDouble(),
    );
  }
}

// 장바구니 모델
class Cart {
  final String userId;
  final int? currentStoreId; // Flutter에서는 int?, 백엔드 Cart 도메인은 String storeId
  final Map<String, CartItem> items;

  Cart({
    required this.userId,
    this.currentStoreId,
    Map<String, CartItem>? items,
  }) : items = items ?? {};

  double get totalAmount {
    if (items.isEmpty) return 0.0;
    return items.values
        .map((item) => item.totalPrice)
        .reduce((sum, price) => sum + price);
  }

  int get totalItems {
    if (items.isEmpty) return 0;
    return items.values.map((item) => item.quantity).reduce((sum, qty) => sum + qty);
  }

  Map<String, dynamic> toJson() => {
    'userId': userId,
    // currentStoreId를 String으로 변환하여 전송 (백엔드 Cart 도메인의 storeId가 String이므로)
    'storeId': currentStoreId?.toString(), // 필드명을 'storeId'로 변경하고 String으로 변환
    'cartItems': items.map((key, value) => MapEntry(key, value.toJson())), // 필드명을 'cartItems'로 변경
  };

  factory Cart.fromJson(Map<String, dynamic> json) {
    // 백엔드 응답의 "cartItems" 키를 사용하여 아이템 맵 파싱
    var itemsMapFromJson = json['cartItems'] as Map<String, dynamic>?;
    Map<String, CartItem> parsedItems = {};
    if (itemsMapFromJson != null) {
      parsedItems = itemsMapFromJson.map((key, value) =>
          MapEntry(key, CartItem.fromJson(value as Map<String, dynamic>)));
    }

    int? storeIdAsInt;
    if (json['storeId'] != null) { // 백엔드 Cart 도메인의 필드명은 'storeId' (String 타입)
      storeIdAsInt = int.tryParse(json['storeId'].toString());
    }

    return Cart(
      userId: json['userId'] as String,
      currentStoreId: storeIdAsInt, // 파싱된 int? 값 사용
      items: parsedItems,
    );
  }
}

// AddItemToCartRequest (storeId를 String으로 보내도록 toJson 수정)
class AddItemToCartRequest {
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice;
  final int storeId; // Flutter에서는 int로 관리

  AddItemToCartRequest({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
    required this.storeId,
  });

  Map<String, dynamic> toJson() => {
    'menuId': menuId,
    'menuName': menuName,
    'quantity': quantity,
    'unitPrice': unitPrice,
    'storeId': storeId.toString(), // 백엔드 AddItemRequest는 String storeId를 기대
  };
}

// UpdateCartItemQuantityRequest (변경 없음)
class UpdateCartItemQuantityRequest {
  final String menuId;
  final int quantity;

  UpdateCartItemQuantityRequest({required this.menuId, required this.quantity});

  Map<String, dynamic> toJson() => {
    'menuId': menuId,
    'quantity': quantity,
  };
}

// CartItemDtoForOrder (변경 없음)
class CartItemDtoForOrder {
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice;

  CartItemDtoForOrder({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
  });

  Map<String, dynamic> toJsonForCartItemDto() {
    return {
      'menuId': menuId,
      'menuName': menuName,
      'quantity': quantity,
      'unitPrice': unitPrice,
    };
  }
}

// CreateOrderRequestDtoForFlutter (storeId를 String으로 보내도록 toJson 수정)
class CreateOrderRequestDtoForFlutter {
  final String storeId; // 백엔드 CreateOrderRequest는 String storeId
  final List<CartItemDtoForOrder> cartItems;

  CreateOrderRequestDtoForFlutter({
    required this.storeId,
    required this.cartItems,
  });

  Map<String, dynamic> toJson() {
    return {
      'storeId': storeId, // String 타입 그대로 사용
      'cartItems': cartItems.map((item) => item.toJsonForCartItemDto()).toList(),
    };
  }
}

// CheckoutResult (변경 없음)
class CheckoutResult {
  final bool success;
  final String? errorMessage;
  final int? orderId;

  CheckoutResult({required this.success, this.errorMessage, this.orderId});

  factory CheckoutResult.fromJson(Map<String, dynamic> json) {
    return CheckoutResult(
      success: json['success'] as bool? ?? (json['orderId'] != null && json['orderId'] > 0),
      errorMessage: json['errorMessage'] as String? ?? json['message'] as String?,
      orderId: json['orderId'] as int?,
    );
  }
}
