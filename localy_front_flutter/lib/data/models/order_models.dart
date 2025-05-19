// 파일 위치: lib/data/models/order_models.dart
import 'package:flutter/foundation.dart';

// 주문 생성 요청 시 장바구니 아이템 정보를 담는 DTO
// 백엔드의 order_service.order.dto.CartItemDto 와 동일 구조
@immutable
class OrderCartItemDto {
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice; // BigDecimal은 Dart에서 double로

  const OrderCartItemDto({
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
  });

  Map<String, dynamic> toJson() {
    return {
      'menuId': menuId,
      'menuName': menuName,
      'quantity': quantity,
      'unitPrice': unitPrice,
    };
  }

// (선택) CartItem으로부터 변환하는 팩토리 생성자
// factory OrderCartItemDto.fromCartItem(CartItem cartItem) {
//   return OrderCartItemDto(
//     menuId: cartItem.menuId,
//     menuName: cartItem.menuName,
//     quantity: cartItem.quantity,
//     unitPrice: cartItem.unitPrice,
//   );
// }
}

// 주문 생성 요청 시 사용될 데이터 모델
// 백엔드의 order_service.order.dto.CreateOrderRequest 와 동일 구조
@immutable
class CreateOrderRequest {
  // final String userId; // 서버에서 X-User-Id 헤더를 통해 자동으로 채워지므로, 클라이언트 요청 본문에는 불필요
  final String storeId;
  final List<OrderCartItemDto> cartItems;

  const CreateOrderRequest({
    // required this.userId, // API 서비스에서 헤더로 주입되므로 생성자에서는 제외 가능
    required this.storeId,
    required this.cartItems,
  });

  Map<String, dynamic> toJson() {
    return {
      // 'userId': userId, // 실제 요청 시에는 서버에서 채워짐
      'storeId': storeId,
      'cartItems': cartItems.map((item) => item.toJson()).toList(),
    };
  }
}

// 주문 내역의 개별 항목을 나타내는 데이터 모델
@immutable
class OrderLineItem {
  final int orderLineItemId; // Long은 Dart에서 int로
  final String menuId;
  final String menuName;
  final int quantity;
  final double unitPrice;
  final double totalPrice;
  final DateTime createdAt;

  const OrderLineItem({
    required this.orderLineItemId,
    required this.menuId,
    required this.menuName,
    required this.quantity,
    required this.unitPrice,
    required this.totalPrice,
    required this.createdAt,
  });

  factory OrderLineItem.fromJson(Map<String, dynamic> json) {
    return OrderLineItem(
      orderLineItemId: json['orderItemId'] as int? ?? 0,
      menuId: json['menuId'] as String? ?? '',
      menuName: json['menuName'] as String? ?? '알 수 없는 메뉴',
      quantity: json['quantity'] as int? ?? 0,
      unitPrice: (json['unitPrice'] as num?)?.toDouble() ?? 0.0,
      totalPrice: (json['totalPrice'] as num?)?.toDouble() ?? 0.0,
      createdAt: DateTime.parse(json['createdAt'] as String? ?? DateTime.now().toIso8601String()),
    );
  }
}

// 주문 정보를 담는 데이터 모델
// 백엔드의 order_service.order.domain.Order 와 유사 (또는 OrderResponseDto)
@immutable
class Order {
  final int orderId;
  final String userId;
  final String storeId;
  final List<OrderLineItem> orderLineItems;
  final DateTime orderDate;
  final double totalAmount;
  final String orderStatus; // 예: "PENDING", "PROCESSING", "COMPLETED", "CANCELLED"
  final int? paymentId;     // 결제 완료 후 연결될 결제 ID
  final DateTime createdAt;

  const Order({
    required this.orderId,
    required this.userId,
    required this.storeId,
    required this.orderLineItems,
    required this.orderDate,
    required this.totalAmount,
    required this.orderStatus,
    this.paymentId,
    required this.createdAt,
  });

  factory Order.fromJson(Map<String, dynamic> json) {
    return Order(
      orderId: json['orderId'] as int? ?? 0,
      userId: json['userId'] as String? ?? '',
      storeId: json['storeId'] as String? ?? '',
      orderLineItems: (json['orderLineItems'] as List<dynamic>?)
          ?.map((itemJson) => OrderLineItem.fromJson(itemJson as Map<String, dynamic>))
          .toList() ?? [], // orderLineItems가 null일 경우 빈 리스트
      orderDate: DateTime.parse(json['orderDate'] as String? ?? DateTime.now().toIso8601String()),
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      orderStatus: json['orderStatus'] as String? ?? 'UNKNOWN',
      paymentId: json['paymentId'] as int?,
      createdAt: DateTime.parse(json['createdAt'] as String? ?? DateTime.now().toIso8601String()),
    );
  }
}
