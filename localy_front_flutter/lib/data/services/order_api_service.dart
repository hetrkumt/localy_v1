// 파일 위치: lib/data/services/order_api_service.dart
import 'package:flutter/foundation.dart';
import '../models/order_models.dart'; // 주문 관련 데이터 모델 import
import 'api_client.dart';           // 공통 API 클라이언트 import

class OrderApiService {
  final ApiClient _apiClient;

  OrderApiService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  // 새로운 주문 생성 (인증 필요)
  Future<Order> placeOrder(CreateOrderRequest orderRequest) async {
    try {
      // CreateOrderRequest 모델의 toJson()은 userId를 포함하지 않도록 수정되었거나,
      // 서버에서 요청 본문의 userId를 무시하고 X-User-Id 헤더를 사용해야 합니다.
      final response = await _apiClient.post('/orders', orderRequest.toJson());
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      debugPrint("OrderApiService: 주문 생성 성공, 주문 ID: ${responseData['orderId']}");
      return Order.fromJson(responseData);
    } catch (e) {
      debugPrint("OrderApiService: 주문 생성 실패 - $e");
      throw Exception("주문을 생성하는데 실패했습니다. 장바구니 상태를 확인해주세요.");
    }
  }

// TODO: 주문 목록 조회 API (백엔드에 해당 API가 있다면 추가)
// 예시:
// Future<List<Order>> getMyOrders() async {
//   try {
//     final response = await _apiClient.get('/orders'); // 실제 엔드포인트 확인 필요
//     final List<dynamic> responseData = _apiClient.processResponse(response);
//     return responseData.map((json) => Order.fromJson(json)).toList();
//   } catch (e) {
//     debugPrint("OrderApiService: 내 주문 목록 조회 실패 - $e");
//     throw Exception("주문 내역을 가져오는데 실패했습니다.");
//   }
// }

// TODO: 특정 주문 상세 조회 API (백엔드에 해당 API가 있다면 추가)
// 예시:
// Future<Order> getOrderDetails(int orderId) async {
//   try {
//     final response = await _apiClient.get('/orders/$orderId'); // 실제 엔드포인트 확인 필요
//     final Map<String, dynamic> responseData = _apiClient.processResponse(response);
//     return Order.fromJson(responseData);
//   } catch (e) {
//     debugPrint("OrderApiService: 주문 상세 조회 실패 (OrderID: $orderId) - $e");
//     throw Exception("주문 상세 정보를 가져오는데 실패했습니다.");
//   }
// }
}
