// 파일 위치: lib/data/services/auth_api_service.dart
import 'dart:convert'; // json.decode 사용
import 'package:flutter/foundation.dart';
// 현재 파일(auth_api_service.dart)은 lib/data/services/ 폴더에 있습니다.
// auth_models.dart는 lib/data/models/ 폴더에 있으므로, 상대 경로는 '../models/auth_models.dart'가 됩니다.
import '../models/auth_models.dart'; // 인증 관련 데이터 모델 import
// api_client.dart는 현재 파일과 같은 services 폴더에 있으므로, 상대 경로는 'api_client.dart' 또는 './api_client.dart'가 됩니다.
import 'api_client.dart';           // 공통 API 클라이언트 import

class AuthApiService {
  final ApiClient _apiClient;

  AuthApiService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  Future<LoginResponse> login(LoginRequest loginRequest) async {
    try {
      final response = await _apiClient.post(
        '/auth/login',
        loginRequest.toJson(),
        requiresAuth: false,
      );
      final Map<String, dynamic> responseData = _apiClient.processResponse(
        response,
        expectFullJsonResponse: true,
      ) as Map<String, dynamic>;
      final loginResponse = LoginResponse.fromJson(responseData);
      await _apiClient.saveAuthToken(loginResponse.accessToken);
      debugPrint("AuthApiService: 로그인 성공, 토큰 저장됨. AccessToken: ${loginResponse.accessToken}");
      return loginResponse;
    } catch (e) {
      debugPrint("AuthApiService: 로그인 실패 - $e");
      if (e.toString().contains("invalid_grant") || e.toString().contains("아이디 또는 비밀번호")) {
        throw Exception("아이디 또는 비밀번호가 올바르지 않습니다.");
      }
      throw Exception("로그인에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
  }

  Future<String> logout() async {
    try {
      final response = await _apiClient.post('/auth/logout', {}, requiresAuth: true);
      await _apiClient.deleteAuthTokens();
      debugPrint("AuthApiService: 로그아웃 성공, 토큰 삭제됨.");
      return _apiClient.processResponse(response) as String;
    } catch (e) {
      debugPrint("AuthApiService: 로그아웃 실패 - $e");
      await _apiClient.deleteAuthTokens();
      throw Exception("로그아웃 중 오류가 발생했습니다.");
    }
  }

  Future<void> register(UserRegistrationRequest registrationRequest) async {
    try {
      final response = await _apiClient.post(
        '/users',
        registrationRequest.toJson(),
        requiresAuth: false,
      );
      _apiClient.processResponse(response);
      debugPrint("AuthApiService: 회원가입 요청 성공.");
    } catch (e) {
      debugPrint("AuthApiService: 회원가입 실패 - $e");
      if (e.toString().contains("이미 사용 중인")) {
        throw Exception("이미 사용 중인 사용자명 또는 이메일입니다.");
      }
      throw Exception("회원가입 중 오류가 발생했습니다.");
    }
  }

  Future<bool> isUserLoggedIn() async {
    final String? token = await _apiClient.getAuthToken();
    debugPrint("AuthApiService: 현재 저장된 토큰 - ${token != null && token.isNotEmpty ? '있음' : '없음'}");
    return token != null && token.isNotEmpty;
  }

  Future<String?> getCurrentAccessToken() async {
    return await _apiClient.getAuthToken();
  }
}
