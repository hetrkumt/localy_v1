// 파일 위치: lib/data/services/order_api_service.dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:localy_front_flutter/data/models/order_models.dart';
import 'package:localy_front_flutter/data/services/api_client.dart';
import 'package:localy_front_flutter/core/config/app_config.dart';

// UriBuilder 헬퍼 클래스 정의
// 다른 파일 (예: store_api_service.dart 또는 별도 유틸리티 파일)에 이미 정의되어 있다면,
// 이 부분을 삭제하고 해당 파일을 import 하세요.
// 여기서는 이 파일 내에 직접 정의합니다.
class UriBuilder {
  String scheme;
  String host;
  int? port;
  String path;
  Map<String, String>? queryParams;

  UriBuilder({
    required this.scheme,
    required this.host,
    this.port,
    required this.path,
    this.queryParams,
  });

  Uri build() {
    return Uri(
      scheme: scheme,
      host: host,
      port: port,
      path: path,
      queryParameters: queryParams,
    );
  }
}

class OrderApiService {
  final ApiClient apiClient;

  OrderApiService({required this.apiClient});

  Future<List<Order>> fetchUserOrders({int page = 0, int size = 20}) async {
    // UriBuilder 클래스를 사용하여 URI 생성
    final UriBuilder uriBuilder = UriBuilder(
      scheme: Uri.parse(AppConfig.baseUrl).scheme,
      host: Uri.parse(AppConfig.baseUrl).host,
      port: Uri.parse(AppConfig.baseUrl).port,
      path: '${Uri.parse(AppConfig.baseUrl).path}/orders', // AppConfig.baseUrl에 /api가 포함되어 있다고 가정
    );
    uriBuilder.queryParams = {
      'page': page.toString(),
      'size': size.toString(),
    };
    final Uri uri = uriBuilder.build();
    debugPrint('--- OrderApiService: Fetching user orders from URL: $uri ---');

    try {
      final response = await apiClient.get(uri.toString());
      if (response.statusCode == 200) {
        final List<dynamic> responseData = json.decode(utf8.decode(response.bodyBytes));
        List<Order> orders = responseData.map((data) => Order.fromJson(data)).toList();
        debugPrint('--- OrderApiService: Fetched ${orders.length} orders ---');
        return orders;
      } else {
        debugPrint('--- OrderApiService: Failed to load orders. Status: ${response.statusCode}, Body: ${response.body}');
        throw Exception('Failed to load orders: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('--- OrderApiService: Error fetching orders: $e ---');
      rethrow;
    }
  }

  Future<Order?> fetchOrderDetail(int orderId) async {
    // AppConfig.baseUrl에 /api가 포함되어 있다고 가정
    final String url = '${AppConfig.baseUrl}/orders/$orderId';
    debugPrint('--- OrderApiService: Fetching order detail from URL: $url ---');
    try {
      final response = await apiClient.get(url);
      if (response.statusCode == 200) {
        return Order.fromJson(json.decode(utf8.decode(response.bodyBytes)));
      } else if (response.statusCode == 404) {
        return null; // 주문을 찾을 수 없음
      } else {
        throw Exception('Failed to load order detail: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('--- OrderApiService: Error fetching order detail: $e ---');
      rethrow;
    }
  }

  Future<Order> createOrder(Map<String, dynamic> orderData) async {
    debugPrint("OrderApiService: createOrder 호출 - Data: $orderData");
    try {
      // AppConfig.baseUrl에 /api가 포함되어 있다고 가정
      final response = await apiClient.post(
        '/orders', // ApiClient 내부에서 AppConfig.baseUrl과 합쳐짐
        orderData,
      );

      if (response.statusCode == 201 || response.statusCode == 200) {
        final responseBody = json.decode(utf8.decode(response.bodyBytes));
        debugPrint("OrderApiService: Order created successfully - Response: $responseBody");
        return Order.fromJson(responseBody);
      } else {
        debugPrint('OrderApiService: Failed to create order - Status: ${response.statusCode}, Body: ${response.body}');
        String errorMessage = "주문 생성에 실패했습니다.";
        try {
          final errorBody = json.decode(utf8.decode(response.bodyBytes));
          if (errorBody['message'] != null) {
            errorMessage = errorBody['message'];
          } else if (errorBody['error'] != null) {
            errorMessage = errorBody['error'];
          }
        } catch (_) {
          // 오류 메시지 파싱 실패 시 기본 메시지 사용
        }
        throw Exception(errorMessage);
      }
    } catch (e) {
      debugPrint('OrderApiService: createOrder 중 예외 발생 - $e');
      rethrow;
    }
  }
}
