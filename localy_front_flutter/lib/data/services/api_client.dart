// 파일 위치: lib/data/services/api_client.dart
import 'dart:convert';
import 'package:flutter/foundation.dart'; // debugPrint 사용을 위해 추가 (선택 사항)
import 'package:http/http.dart' as http;
import 'package:localy_front_flutter/core/config/app_config.dart'; // AppConfig 임포트

class ApiClient {
  String? _token; // 내부적으로 토큰 저장

  // 누락되었던 token getter 추가!
  String? get token => _token;

  // 토큰 업데이트 메서드
  void updateToken(String? newToken) {
    _token = newToken;
    // debugPrint("ApiClient: Token updated to: $_token"); // 디버깅용
  }

  Future<http.Response> get(String endpoint, {Map<String, String>? headers}) async {
    final url = Uri.parse(endpoint.startsWith('http') ? endpoint : '${AppConfig.baseUrl}$endpoint');
    final defaultHeaders = {
      'Content-Type': 'application/json; charset=UTF-8',
      if (_token != null) 'Authorization': 'Bearer $_token',
    };
    if (headers != null) {
      defaultHeaders.addAll(headers);
    }
    return http.get(url, headers: defaultHeaders);
  }

  Future<http.Response> post(String endpoint, dynamic body, {Map<String, String>? headers}) async {
    final url = Uri.parse(endpoint.startsWith('http') ? endpoint : '${AppConfig.baseUrl}$endpoint');
    final defaultHeaders = {
      'Content-Type': 'application/json; charset=UTF-8',
      if (_token != null) 'Authorization': 'Bearer $_token',
    };
    if (headers != null) {
      defaultHeaders.addAll(headers);
    }
    return http.post(url, headers: defaultHeaders, body: json.encode(body));
  }

  Future<http.Response> put(String endpoint, dynamic body, {Map<String, String>? headers}) async {
    final url = Uri.parse(endpoint.startsWith('http') ? endpoint : '${AppConfig.baseUrl}$endpoint');
    final defaultHeaders = {
      'Content-Type': 'application/json; charset=UTF-8',
      if (_token != null) 'Authorization': 'Bearer $_token',
    };
    if (headers != null) {
      defaultHeaders.addAll(headers);
    }
    return http.put(url, headers: defaultHeaders, body: json.encode(body));
  }

  Future<http.Response> delete(String endpoint, {Map<String, String>? headers}) async {
    final url = Uri.parse(endpoint.startsWith('http') ? endpoint : '${AppConfig.baseUrl}$endpoint');
    final defaultHeaders = {
      'Content-Type': 'application/json; charset=UTF-8',
      if (_token != null) 'Authorization': 'Bearer $_token',
    };
    if (headers != null) {
      defaultHeaders.addAll(headers);
    }
    return http.delete(url, headers: defaultHeaders);
  }

  // Multipart 요청 메서드 (파일 업로드 시 필요)
  Future<http.StreamedResponse> multipartRequest(
      String method, // "POST" 또는 "PUT"
      String endpoint,
      Map<String, String> fields,
      List<http.MultipartFile> files, {
        Map<String, String>? headers,
      }) async {
    final url = Uri.parse(endpoint.startsWith('http') ? endpoint : '${AppConfig.baseUrl}$endpoint');
    var request = http.MultipartRequest(method, url);

    final defaultHeaders = {
      if (_token != null) 'Authorization': 'Bearer $_token',
    };
    if (headers != null) {
      defaultHeaders.addAll(headers);
    }
    request.headers.addAll(defaultHeaders);
    request.fields.addAll(fields);
    request.files.addAll(files);

    return request.send();
  }
}
