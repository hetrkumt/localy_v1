// 파일 위치: lib/presentation/widgets/order_card.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart'; // 날짜 포맷팅을 위해
import 'package:localy_front_flutter/data/models/order_models.dart';
import 'package:localy_front_flutter/data/models/store_models.dart'; // Store 모델 임포트
import 'package:localy_front_flutter/presentation/providers/store_provider.dart'; // StoreProvider 임포트
import 'package:provider/provider.dart'; // Provider 임포트

class OrderCard extends StatelessWidget {
  final Order order;
  final VoidCallback? onTap; // 주문 상세 보기 등으로 이동하기 위한 콜백

  const OrderCard({
    super.key,
    required this.order,
    this.onTap,
  });

  // 주문 상태에 따른 아이콘과 색상, 한글 텍스트를 반환하는 헬퍼 함수
  Widget _buildOrderStatusWidget(String status, BuildContext context) {
    IconData statusIcon;
    Color statusColor;
    String statusText;

    // 백엔드에서 오는 orderStatus 문자열을 기준으로 분기
    switch (status.toUpperCase()) {
      case 'PENDING':
        statusIcon = Icons.hourglass_top_rounded;
        statusColor = Colors.orange.shade700;
        statusText = '처리중';
        break;
      case 'PROCESSING': // 백엔드에서 정의한 상태값에 따라 추가
        statusIcon = Icons.sync_rounded;
        statusColor = Colors.blue.shade700;
        statusText = '준비중';
        break;
      case 'COMPLETED': // 백엔드에서 'COMPLETED'로 온다고 가정
        statusIcon = Icons.check_circle_outline_rounded;
        statusColor = Colors.green.shade700;
        statusText = '완료됨';
        break;
      case 'DELIVERED': // 예시 상태
        statusIcon = Icons.local_shipping_rounded;
        statusColor = Colors.teal.shade700;
        statusText = '배달완료';
        break;
      case 'CANCELLED':
      case 'FAILED':
        statusIcon = Icons.cancel_outlined;
        statusColor = Colors.red.shade700;
        statusText = '취소/실패';
        break;
      default:
        statusIcon = Icons.help_outline_rounded;
        statusColor = Theme.of(context).textTheme.bodySmall?.color ?? Colors.grey;
        statusText = status; // 알 수 없는 상태는 원본 문자열 표시
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(statusIcon, color: statusColor, size: 16),
        const SizedBox(width: 4),
        Text(
          statusText,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: statusColor,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy년 MM월 dd일 HH:mm'); // 날짜 형식 지정
    final ThemeData theme = Theme.of(context); // 현재 테마 가져오기

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0), // 마진 조정
      elevation: 2.5, // 그림자 약간 더
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)), // 모서리 둥글게
      child: InkWell(
        onTap: onTap, // 탭 콜백 연결
        borderRadius: BorderRadius.circular(12.0),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 주문번호 및 상태
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.start, // 상단 정렬
                children: [
                  Expanded(
                    child: Text(
                      '주문번호: ${order.orderId}',
                      style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ),
                  _buildOrderStatusWidget(order.orderStatus, context), // 주문 상태 위젯 사용
                ],
              ),
              const SizedBox(height: 6),
              // 주문일시
              Text(
                '주문일시: ${dateFormat.format(order.orderDate)}', // 포맷된 날짜 사용
                style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey[700]),
              ),
              const SizedBox(height: 10),

              // 가게 이름 (FutureBuilder 사용)
              FutureBuilder<Store?>(
                // StoreProvider의 getStoreById 메서드 호출 (비동기)
                future: Provider.of<StoreProvider>(context, listen: false).getStoreById(order.storeId),
                builder: (context, snapshot) {
                  String storeDisplayName = '가게 정보 로딩 중...';
                  if (snapshot.connectionState == ConnectionState.done) {
                    if (snapshot.hasError || !snapshot.hasData || snapshot.data == null) {
                      storeDisplayName = '가게 정보 없음 (ID: ${order.storeId})';
                    } else {
                      storeDisplayName = snapshot.data!.name;
                    }
                  }
                  return Row(
                    children: [
                      Icon(Icons.store_mall_directory_outlined, size: 16, color: Colors.grey[700]),
                      const SizedBox(width: 6),
                      Expanded(child: Text(storeDisplayName, style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500))),
                    ],
                  );
                },
              ),
              const SizedBox(height: 10),
              const Divider(),
              const SizedBox(height: 10),

              // 주문 상품 목록
              Text(
                '주문 상품:',
                style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              if (order.orderLineItems.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8.0),
                  child: Text('주문 상품 정보가 없습니다.', style: TextStyle(color: Colors.grey)),
                )
              else
              // 최대 2개 상품만 간략히 표시, 나머지는 "외 N건"
                ...order.orderLineItems.take(2).map((item) => Padding(
                  padding: const EdgeInsets.only(bottom: 4.0, top: 2.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          '${item.menuName} × ${item.quantity}',
                          style: theme.textTheme.bodyMedium,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Text(
                        '${item.totalPrice.toStringAsFixed(0)}원',
                        style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
                      ),
                    ],
                  ),
                )),
              if (order.orderLineItems.length > 2)
                Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Text(
                    '... 외 ${order.orderLineItems.length - 2}건 더보기',
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.primaryColor, fontWeight: FontWeight.w500),
                  ),
                ),
              const SizedBox(height: 12),

              // 총 결제액
              Align(
                alignment: Alignment.centerRight,
                child: Text(
                  '총 결제액: ${order.totalAmount.toStringAsFixed(0)}원',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.primaryColorDark, // 좀 더 진한 색상
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
