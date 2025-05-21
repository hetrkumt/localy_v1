// 파일 위치: lib/presentation/widgets/order_card.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart'; // 날짜 포맷팅을 위해 (pubspec.yaml에 intl 추가 필요)
import 'package:localy_front_flutter/data/models/order_models.dart';

class OrderCard extends StatelessWidget {
  final Order order;
  final VoidCallback? onTap; // 주문 상세 보기 등으로 이동하기 위한 콜백

  const OrderCard({
    super.key,
    required this.order,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy년 MM월 dd일 HH:mm'); // 날짜 형식

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
      elevation: 2.0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10.0)),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10.0),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '주문 #${order.orderId}',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  Text(
                    order.orderStatus, // TODO: 주문 상태 한글화 또는 아이콘/색상으로 구분
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: _getStatusColor(order.orderStatus, context),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                '주문일시: ${dateFormat.format(order.orderDate)}',
                style: TextStyle(fontSize: 13, color: Colors.grey[700]),
              ),
              const SizedBox(height: 4),
              // TODO: 가게 이름 표시 (order.storeId를 사용하여 StoreProvider 등에서 가게 정보 조회)
              // Text('가게: ${order.storeId}번 가게', style: TextStyle(fontSize: 13, color: Colors.grey[700])),
              // const SizedBox(height: 8),
              if (order.orderLineItems.isNotEmpty)
                Text(
                  '주요 상품: ${order.orderLineItems.first.menuName}${order.orderLineItems.length > 1 ? ' 외 ${order.orderLineItems.length - 1}건' : ''}',
                  style: TextStyle(fontSize: 14, color: Colors.grey[800]),
                  overflow: TextOverflow.ellipsis,
                ),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: Text(
                  '총 ${order.totalAmount.toStringAsFixed(0)}원',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).primaryColor,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Color _getStatusColor(String status, BuildContext context) {
    // TODO: 백엔드 주문 상태 문자열에 맞춰 색상 정의
    switch (status.toUpperCase()) {
      case 'PENDING':
      case 'PROCESSING':
        return Colors.orange.shade700;
      case 'COMPLETED':
      case 'DELIVERED': // 예시 상태
        return Colors.green.shade700;
      case 'CANCELLED':
      case 'FAILED': // 예시 상태
        return Colors.red.shade700;
      default:
        return Theme.of(context).textTheme.bodySmall?.color ?? Colors.grey;
    }
  }
}
