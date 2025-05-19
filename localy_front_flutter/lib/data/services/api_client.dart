// 파일 위치: lib/data/services/api_client.dart
import 'dart:convert'; // utf8, jsonEncode, jsonDecode 사용을 위해 필수
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../core/config/app_config.dart';

class ApiClient {
  final http.Client _httpClient;
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
  static const String _authTokenKey = 'user_auth_token';

  ApiClient({http.Client? httpClient}) : _httpClient = httpClient ?? http.Client();

  Future<String?> getAuthToken() async {
    return await _secureStorage.read(key: _authTokenKey);
  }

  Future<void> saveAuthToken(String token) async {
    await _secureStorage.write(key: _authTokenKey, value: token);
    debugPrint("ApiClient: 액세스 토큰 저장됨.");
  }

  Future<void> deleteAuthTokens() async {
    await _secureStorage.delete(key: _authTokenKey);
    debugPrint("ApiClient: 저장된 토큰들 삭제됨.");
  }

  Future<Map<String, String>> _createHeaders({
    bool requiresAuth = true,
    bool isMultipart = false,
    bool isFormUrlEncoded = false,
  }) async {
    final headers = <String, String>{};
    if (isFormUrlEncoded) {
      headers[HttpHeaders.contentTypeHeader] = 'application/x-www-form-urlencoded';
    } else if (!isMultipart) {
      headers[HttpHeaders.contentTypeHeader] = 'application/json; charset=UTF-8';
    }
    headers[HttpHeaders.acceptHeader] = 'application/json, text/plain, */*';

    if (requiresAuth) {
      final String? token = await getAuthToken();
      if (token != null && token.isNotEmpty) {
        headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
      } else {
        debugPrint("ApiClient: 인증 토큰이 필요하지만 현재 토큰이 없습니다.");
      }
    }
    return headers;
  }

  Future<http.Response> get(String endpoint, {bool requiresAuth = true}) async {
    final url = Uri.parse('${AppConfig.baseUrl}$endpoint');
    debugPrint('ApiClient GET: $url');
    final headers = await _createHeaders(requiresAuth: requiresAuth);
    return _httpClient.get(url, headers: headers).timeout(const Duration(seconds: 15));
  }

  Future<http.Response> post(String endpoint, Map<String, dynamic> body, {bool requiresAuth = true}) async {
    final url = Uri.parse('${AppConfig.baseUrl}$endpoint');
    debugPrint('ApiClient POST (JSON): $url, Body: ${jsonEncode(body)}');
    final headers = await _createHeaders(requiresAuth: requiresAuth);
    return _httpClient.post(url, headers: headers, body: jsonEncode(body)).timeout(const Duration(seconds: 15));
  }

  Future<http.Response> postForm(String fullUrl, Map<String, String> body, {bool requiresAuth = false}) async {
    final url = Uri.parse(fullUrl);
    debugPrint('ApiClient POST (Form): $url, FormBody: $body');
    final headers = await _createHeaders(requiresAuth: requiresAuth, isFormUrlEncoded: true);
    return _httpClient.post(url, headers: headers, body: body).timeout(const Duration(seconds: 15));
  }

  Future<http.Response> put(String endpoint, Map<String, dynamic> body, {bool requiresAuth = true}) async {
    final url = Uri.parse('${AppConfig.baseUrl}$endpoint');
    debugPrint('ApiClient PUT: $url, Body: ${jsonEncode(body)}');
    final headers = await _createHeaders(requiresAuth: requiresAuth);
    return _httpClient.put(url, headers: headers, body: jsonEncode(body)).timeout(const Duration(seconds: 15));
  }

  Future<http.Response> delete(String endpoint, {Map<String, dynamic>? body, bool requiresAuth = true}) async {
    final url = Uri.parse('${AppConfig.baseUrl}$endpoint');
    debugPrint('ApiClient DELETE: $url, Body: ${body != null ? jsonEncode(body) : "N/A"}');
    final headers = await _createHeaders(requiresAuth: requiresAuth);
    if (body != null) {
      return _httpClient.delete(url, headers: headers, body: jsonEncode(body)).timeout(const Duration(seconds: 15));
    }
    return _httpClient.delete(url, headers: headers).timeout(const Duration(seconds: 15));
  }

  Future<http.StreamedResponse> multipartRequest(
      String httpMethod,
      String endpoint,
      Map<String, String> fields,
      List<http.MultipartFile> files,
      {bool requiresAuth = true}
      ) async {
    final url = Uri.parse('${AppConfig.baseUrl}$endpoint');
    debugPrint('ApiClient Multipart $httpMethod: $url, Fields: $fields, Files: ${files.map((f) => f.filename).join(', ')}');

    final request = http.MultipartRequest(httpMethod, url);
    request.headers.addAll(await _createHeaders(requiresAuth: requiresAuth, isMultipart: true));
    request.fields.addAll(fields);
    request.files.addAll(files);

    return request.send().timeout(const Duration(seconds: 60));
  }

  // HTTP 응답 처리 (expectFullJsonResponse 파라미터로 JSON 객체 반환 여부 결정)
  dynamic processResponse(http.Response response, {bool expectFullJsonResponse = false}) {
    // UTF-8 디코딩을 위해 dart:convert의 utf8 사용
    final String responseBodyString = utf8.decode(response.bodyBytes);
    debugPrint('ApiClient Response Status: ${response.statusCode}, Body: $responseBodyString');

    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (responseBodyString.isEmpty || response.statusCode == 204) {
        return null; // 성공, 본문 없음
      }
      // expectFullJsonResponse가 true이면 항상 JSON 객체로 디코딩 시도
      if (expectFullJsonResponse) {
        try {
          return json.decode(responseBodyString);
        } catch (e) {
          debugPrint("ApiClient: JSON 디코딩 실패 (expectFullJsonResponse=true). 오류: $e. 원본 문자열: $responseBodyString");
          // JSON 파싱 실패 시에도 오류를 던져서 호출부에서 인지하도록 함
          throw Exception('서버 응답을 JSON으로 파싱하는데 실패했습니다.');
        }
      }
      // expectFullJsonResponse가 false이면 (또는 명시되지 않으면) 원본 문자열 반환 (예: 단순 토큰 문자열)
      return responseBodyString;
    } else if (response.statusCode == 401 || response.statusCode == 403) {
      deleteAuthTokens();
      throw Exception('인증/인가 오류 (${response.statusCode}): $responseBodyString');
    } else {
      try {
        final decodedBody = json.decode(responseBodyString);
        if (decodedBody is Map && decodedBody.containsKey('message')) {
          throw Exception('API 오류 (${response.statusCode}): ${decodedBody['message']}');
        } else if (decodedBody is Map && decodedBody.containsKey('error_description')) {
          throw Exception('API 오류 (${response.statusCode}): ${decodedBody['error_description']}');
        }
      } catch (_) {
        // JSON 파싱 실패 시 원본 본문 사용
      }
      throw Exception('API 오류 (${response.statusCode}): $responseBodyString');
    }
  }
}
