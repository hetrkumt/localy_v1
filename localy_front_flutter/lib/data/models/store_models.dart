// 파일 위치: lib/data/models/store_models.dart
import 'package:flutter/foundation.dart';

// 가게 상태를 나타내는 Enum
enum StoreStatus { OPEN, PREPARING, CLOSED, UNKNOWN }

// 가게 카테고리를 나타내는 Enum
enum StoreCategory { KOREAN, PIZZA, CAFE, CHINESE, JAPANESE, FAST_FOOD, UNKNOWN }

// 문자열을 StoreStatus Enum으로 변환하는 함수
StoreStatus storeStatusFromString(String? statusString) {
  switch (statusString?.toUpperCase()) {
    case 'OPEN':
      return StoreStatus.OPEN;
    case 'PREPARING':
      return StoreStatus.PREPARING;
    case 'CLOSED':
      return StoreStatus.CLOSED;
    default:
      debugPrint("StoreStatus: 알 수 없는 상태 문자열 '$statusString', UNKNOWN으로 처리합니다.");
      return StoreStatus.UNKNOWN;
  }
}

// StoreStatus Enum을 문자열로 변환하는 함수 (API 요청 시 필요할 수 있음)
String storeStatusToString(StoreStatus status) {
  if (status == StoreStatus.UNKNOWN) return 'UNKNOWN'; // 또는 서버가 기대하는 기본값
  return status.toString().split('.').last; // 예: "OPEN"
}

// 문자열을 StoreCategory Enum으로 변환하는 함수
StoreCategory storeCategoryFromString(String? categoryString) {
  switch (categoryString?.toUpperCase()) {
    case 'KOREAN':
      return StoreCategory.KOREAN;
    case 'PIZZA':
      return StoreCategory.PIZZA;
    case 'CAFE':
      return StoreCategory.CAFE;
    case 'CHINESE':
      return StoreCategory.CHINESE;
    case 'JAPANESE':
      return StoreCategory.JAPANESE;
    case 'FAST_FOOD': // 백엔드 Enum 값과 일치하도록
      return StoreCategory.FAST_FOOD;
    default:
      debugPrint("StoreCategory: 알 수 없는 카테고리 문자열 '$categoryString', UNKNOWN으로 처리합니다.");
      return StoreCategory.UNKNOWN;
  }
}

// StoreCategory Enum을 문자열로 변환하는 함수
String storeCategoryToString(StoreCategory category) {
  if (category == StoreCategory.UNKNOWN) return 'UNKNOWN';
  if (category == StoreCategory.FAST_FOOD) return 'FAST_FOOD'; // 백엔드와 일치
  return category.toString().split('.').last;
}

// 가게 정보를 담는 데이터 모델
@immutable
class Store {
  final int id; // Long은 Dart에서 int로 표현
  final String ownerId;
  final String name;
  final String? description;
  final String? address;
  final double? latitude;  // double? (nullable)
  final double? longitude; // double? (nullable)
  final String? phone;
  final String? openingHours;
  final StoreStatus status;
  final StoreCategory category;
  final DateTime createdAt;
  final DateTime? updatedAt;
  // 메뉴, 리뷰 목록은 상세 조회 API를 통해 별도로 가져오거나,
  // 백엔드 응답에 따라 이 모델에 List<Menu>, List<Review> 형태로 포함될 수 있습니다.
  // 현재는 포함하지 않음.

  const Store({
    required this.id,
    required this.ownerId,
    required this.name,
    this.description,
    this.address,
    this.latitude,
    this.longitude,
    this.phone,
    this.openingHours,
    required this.status,
    required this.category,
    required this.createdAt,
    this.updatedAt,
  });

  factory Store.fromJson(Map<String, dynamic> json) {
    return Store(
      id: json['id'] as int,
      ownerId: json['ownerId'] as String? ?? '', // ownerId가 null일 경우 대비
      name: json['name'] as String? ?? '이름 없음', // name이 null일 경우 대비
      description: json['description'] as String?,
      address: json['address'] as String?,
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      phone: json['phone'] as String?,
      openingHours: json['openingHours'] as String?,
      status: storeStatusFromString(json['status'] as String?),
      category: storeCategoryFromString(json['category'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String? ?? DateTime.now().toIso8601String()), // createdAt이 null일 경우 대비
      updatedAt: json['updatedAt'] == null ? null : DateTime.parse(json['updatedAt'] as String),
    );
  }

  // 가게 생성 또는 수정 시 API 요청 본문으로 사용될 JSON 생성
  Map<String, dynamic> toJson() {
    return {
      // 'id': id, // 수정 시에는 id가 필요할 수 있으나, 생성 시에는 불필요
      // 'ownerId': ownerId, // 서버에서 X-User-Id 헤더를 통해 설정
      'name': name,
      'description': description,
      'address': address,
      'latitude': latitude,
      'longitude': longitude,
      'phone': phone,
      'openingHours': openingHours,
      'status': storeStatusToString(status),
      'category': storeCategoryToString(category),
      // createdAt, updatedAt은 서버에서 자동 관리
    };
  }
}

// 메뉴 정보를 담는 데이터 모델
@immutable
class Menu {
  final int id;
  final int storeId; // 이 메뉴가 속한 가게의 ID
  final String name;
  final String? description;
  final double price; // BigDecimal은 Dart에서 double로 표현
  final String? imageUrl; // 예: "/images/menu_image.jpg" (AppConfig.baseUrl과 조합 필요)
  final bool isAvailable;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Menu({
    required this.id,
    required this.storeId,
    required this.name,
    this.description,
    required this.price,
    this.imageUrl,
    required this.isAvailable,
    required this.createdAt,
    this.updatedAt,
  });

  factory Menu.fromJson(Map<String, dynamic> json) {
    return Menu(
      id: json['id'] as int,
      storeId: json['storeId'] as int? ?? 0, // storeId가 null일 경우 대비
      name: json['name'] as String? ?? '이름 없는 메뉴', // name이 null일 경우 대비
      description: json['description'] as String?,
      price: (json['price'] as num?)?.toDouble() ?? 0.0, // price가 null일 경우 대비
      imageUrl: json['imageUrl'] as String?,
      isAvailable: json['isAvailable'] as bool? ?? true, // 기본값 true
      createdAt: DateTime.parse(json['createdAt'] as String? ?? DateTime.now().toIso8601String()),
      updatedAt: json['updatedAt'] == null ? null : DateTime.parse(json['updatedAt'] as String),
    );
  }

  // 메뉴 생성 또는 수정 시 API 요청 본문으로 사용될 JSON 생성
  Map<String, dynamic> toJson() {
    return {
      // 'id': id, // 수정 시에는 id가 필요할 수 있으나, 생성 시에는 불필요
      'storeId': storeId, // 메뉴가 어떤 가게에 속하는지 명시
      'name': name,
      'description': description,
      'price': price,
      // 'imageUrl': imageUrl, // 이미지 URL은 파일 업로드 후 서버에서 설정되거나 별도 관리
      'isAvailable': isAvailable,
      // createdAt, updatedAt은 서버에서 자동 관리
    };
  }
}

// 리뷰 정보를 담는 데이터 모델
@immutable
class Review {
  final int id;
  final int storeId; // 이 리뷰가 달린 가게의 ID
  final String userId; // 리뷰 작성자의 사용자 ID
  final int rating;    // 평점 (1~5)
  final String? comment;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Review({
    required this.id,
    required this.storeId,
    required this.userId,
    required this.rating,
    this.comment,
    required this.createdAt,
    this.updatedAt,
  });

  factory Review.fromJson(Map<String, dynamic> json) {
    return Review(
      id: json['id'] as int,
      storeId: json['storeId'] as int? ?? 0,
      userId: json['userId'] as String? ?? '',
      rating: json['rating'] as int? ?? 0,
      comment: json['comment'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String? ?? DateTime.now().toIso8601String()),
      updatedAt: json['updatedAt'] == null ? null : DateTime.parse(json['updatedAt'] as String),
    );
  }

  // 리뷰 생성 또는 수정 시 API 요청 본문으로 사용될 JSON 생성
  Map<String, dynamic> toJson() {
    return {
      // 'id': id, // 수정 시에는 id가 필요할 수 있으나, 생성 시에는 불필요
      'storeId': storeId, // 어떤 가게에 대한 리뷰인지 명시
      // 'userId': userId, // 서버에서 X-User-Id 헤더를 통해 설정
      'rating': rating,
      'comment': comment,
      // createdAt, updatedAt은 서버에서 자동 관리
    };
  }
}
