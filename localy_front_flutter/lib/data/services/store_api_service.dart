// 파일 위치: lib/data/services/store_api_service.dart
import 'dart:convert'; // jsonEncode 사용
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart'; // XFile 사용을 위해 추가

import '../models/store_models.dart';
import 'api_client.dart';

class StoreApiService {
  final ApiClient _apiClient;

  StoreApiService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  // --- 가게 (Store) API ---
  Future<List<Store>> getAllStores() async {
    try {
      final response = await _apiClient.get('/stores', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final List<dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return responseData.map((json) => Store.fromJson(json)).toList();
    } catch (e) {
      debugPrint("StoreApiService: 모든 가게 조회 실패 - $e");
      // 오류 메시지를 좀 더 구체적으로 전달
      if (e.toString().contains("subtype of type 'List<dynamic>'")) {
        throw Exception("가게 목록 응답 형식이 잘못되었습니다. 서버 응답을 확인해주세요.");
      }
      throw Exception("가게 목록을 불러오는데 실패했습니다.");
    }
  }

  Future<Store> getStoreById(int storeId) async {
    try {
      final response = await _apiClient.get('/stores/$storeId', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Store.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: ID로 가게 조회 실패 (ID: $storeId) - $e");
      throw Exception("가게 정보를 불러오는데 실패했습니다.");
    }
  }

  Future<List<Store>> searchStoresByName(String name) async {
    try {
      final response = await _apiClient.get('/stores/search?name=${Uri.encodeComponent(name)}', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final List<dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return responseData.map((json) => Store.fromJson(json)).toList();
    } catch (e) {
      debugPrint("StoreApiService: 이름으로 가게 검색 실패 (Name: $name) - $e");
      throw Exception("가게 검색에 실패했습니다.");
    }
  }

  Future<Store> createStore(Store storeData) async {
    try {
      final response = await _apiClient.post('/stores', storeData.toJson());
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Store.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 가게 생성 실패 - $e");
      throw Exception("가게 생성에 실패했습니다. 입력 정보를 확인해주세요.");
    }
  }

  Future<Store> updateStore(int storeId, Store storeData) async {
    try {
      final response = await _apiClient.put('/stores/$storeId', storeData.toJson());
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Store.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 가게 수정 실패 (ID: $storeId) - $e");
      throw Exception("가게 정보 수정에 실패했습니다.");
    }
  }

  Future<void> deleteStore(int storeId) async {
    try {
      final response = await _apiClient.delete('/stores/$storeId');
      _apiClient.processResponse(response); // 본문 없는 성공 응답 기대
    } catch (e) {
      debugPrint("StoreApiService: 가게 삭제 실패 (ID: $storeId) - $e");
      throw Exception("가게 삭제에 실패했습니다.");
    }
  }

  // --- 메뉴 (Menu) API ---
  Future<Menu> getMenuById(int menuId) async {
    try {
      final response = await _apiClient.get('/menus/$menuId', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Menu.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: ID로 메뉴 조회 실패 (ID: $menuId) - $e");
      throw Exception("메뉴 정보를 불러오는데 실패했습니다.");
    }
  }

  Future<List<Menu>> getMenusByStoreId(int storeId) async {
    try {
      final response = await _apiClient.get('/menus/stores/$storeId/menus', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final List<dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return responseData.map((json) => Menu.fromJson(json)).toList();
    } catch (e) {
      debugPrint("StoreApiService: 특정 가게 메뉴 조회 실패 (StoreID: $storeId) - $e");
      throw Exception("가게의 메뉴 목록을 불러오는데 실패했습니다.");
    }
  }

  Future<Menu> createMenu(Menu menuData, {XFile? imageXFile}) async {
    try {
      final Map<String, String> fields = {
        'menu': jsonEncode(menuData.toJson())
      };
      final List<http.MultipartFile> files = [];
      if (imageXFile != null) {
        files.add(await http.MultipartFile.fromPath(
          'image',
          imageXFile.path,
          filename: imageXFile.name,
        ));
      }
      final streamedResponse = await _apiClient.multipartRequest('POST', '/menus', fields, files);
      final response = await http.Response.fromStream(streamedResponse);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Menu.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 메뉴 생성 실패 - $e");
      throw Exception("메뉴 생성에 실패했습니다. 이미지나 입력 정보를 확인해주세요.");
    }
  }

  Future<Menu> updateMenu(int menuId, Menu menuData, {XFile? imageXFile}) async {
    try {
      final Map<String, String> fields = {
        'menu': jsonEncode(menuData.toJson())
      };
      final List<http.MultipartFile> files = [];
      if (imageXFile != null) {
        files.add(await http.MultipartFile.fromPath(
          'image',
          imageXFile.path,
          filename: imageXFile.name,
        ));
      }
      final streamedResponse = await _apiClient.multipartRequest('PUT', '/menus/$menuId', fields, files);
      final response = await http.Response.fromStream(streamedResponse);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Menu.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 메뉴 수정 실패 (ID: $menuId) - $e");
      throw Exception("메뉴 수정에 실패했습니다.");
    }
  }

  Future<void> deleteMenu(int menuId) async {
    try {
      final response = await _apiClient.delete('/menus/$menuId');
      _apiClient.processResponse(response);
    } catch (e) {
      debugPrint("StoreApiService: 메뉴 삭제 실패 (ID: $menuId) - $e");
      throw Exception("메뉴 삭제에 실패했습니다.");
    }
  }

  // --- 리뷰 (Review) API ---
  Future<Review> getReviewById(int reviewId) async {
    try {
      final response = await _apiClient.get('/reviews/$reviewId', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Review.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: ID로 리뷰 조회 실패 (ID: $reviewId) - $e");
      throw Exception("리뷰 정보를 불러오는데 실패했습니다.");
    }
  }

  Future<List<Review>> getReviewsByStoreId(int storeId) async {
    try {
      final response = await _apiClient.get('/reviews/stores/$storeId/reviews', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final List<dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return responseData.map((json) => Review.fromJson(json)).toList();
    } catch (e) {
      debugPrint("StoreApiService: 특정 가게 리뷰 조회 실패 (StoreID: $storeId) - $e");
      throw Exception("가게의 리뷰 목록을 불러오는데 실패했습니다.");
    }
  }

  Future<List<Review>> getReviewsByUserId(String userId) async {
    try {
      final response = await _apiClient.get('/reviews/users/$userId/reviews', requiresAuth: false);
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final List<dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return responseData.map((json) => Review.fromJson(json)).toList();
    } catch (e) {
      debugPrint("StoreApiService: 특정 사용자 리뷰 조회 실패 (UserID: $userId) - $e");
      throw Exception("사용자의 리뷰 목록을 불러오는데 실패했습니다.");
    }
  }

  Future<Review> submitReview(Review reviewData) async {
    try {
      final response = await _apiClient.post('/reviews', reviewData.toJson());
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Review.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 리뷰 제출 실패 - $e");
      throw Exception("리뷰 제출에 실패했습니다. 입력 내용을 확인해주세요.");
    }
  }

  Future<Review> updateReview(int reviewId, Review reviewData) async {
    try {
      final response = await _apiClient.put('/reviews/$reviewId', reviewData.toJson());
      // *** 수정된 부분: expectFullJsonResponse: true 추가 ***
      final Map<String, dynamic> responseData = _apiClient.processResponse(response, expectFullJsonResponse: true);
      return Review.fromJson(responseData);
    } catch (e) {
      debugPrint("StoreApiService: 리뷰 수정 실패 (ID: $reviewId) - $e");
      throw Exception("리뷰 수정에 실패했습니다.");
    }
  }

  Future<void> deleteReview(int reviewId) async {
    try {
      final response = await _apiClient.delete('/reviews/$reviewId');
      _apiClient.processResponse(response);
    } catch (e) {
      debugPrint("StoreApiService: 리뷰 삭제 실패 (ID: $reviewId) - $e");
      throw Exception("리뷰 삭제에 실패했습니다.");
    }
  }
}
