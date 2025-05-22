// 파일 위치: lib/data/models/payment_models.dart
import 'package:flutter/foundation.dart';

// 가상 계좌 모델
class VirtualAccount {
  final int accountId; // 백엔드는 Long, Flutter는 int
  final String userId;
  final String? storeId; // 가게 계좌인 경우
  final double balance; // 백엔드는 BigDecimal, Flutter는 double
  final DateTime createdAt;
  final DateTime updatedAt;

  VirtualAccount({
    required this.accountId,
    required this.userId,
    this.storeId,
    required this.balance,
    required this.createdAt,
    required this.updatedAt,
  });

  factory VirtualAccount.fromJson(Map<String, dynamic> json) {
    return VirtualAccount(
      accountId: json['accountId'] as int,
      userId: json['userId'] as String,
      storeId: json['storeId'] as String?,
      balance: (json['balance'] as num).toDouble(),
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}

// 입금 요청 DTO
class DepositRequest {
  final double amount; // 백엔드는 BigDecimal, Flutter는 double

  DepositRequest({required this.amount});

  Map<String, dynamic> toJson() => {
    'amount': amount,
  };
}

// 사용자 계좌 생성 요청 DTO (필요시 사용)
class CreateUserAccountRequestData {
  // final String userId; // 헤더로 전달하므로 DTO에서는 제외
  final double initialBalance;

  CreateUserAccountRequestData({required this.initialBalance});

  Map<String, dynamic> toJson() => {
    'initialBalance': initialBalance,
  };
}

// 가게 계좌 생성 요청 DTO (필요시 사용)
class CreateStoreAccountRequestData {
  final String storeId;
  final String ownerUserId;
  final double initialBalance;

  CreateStoreAccountRequestData({
    required this.storeId,
    required this.ownerUserId,
    required this.initialBalance,
  });

  Map<String, dynamic> toJson() => {
    'storeId': storeId,
    'ownerUserId': ownerUserId,
    'initialBalance': initialBalance,
  };
}
