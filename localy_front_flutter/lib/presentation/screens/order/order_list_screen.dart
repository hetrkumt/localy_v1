// 파일 위치: lib/presentation/screens/order/order_list_screen.dart
import 'package:flutter/material.dart';
import 'package:localy_front_flutter/data/models/order_models.dart'; // Order 모델 임포트
import 'package:localy_front_flutter/presentation/providers/order_provider.dart';
import 'package:localy_front_flutter/presentation/widgets/order_card.dart'; // OrderCard 임포트
import 'package:provider/provider.dart';
// import 'order_detail_screen.dart'; // 주문 상세 화면 (추후 구현 시 필요)

class OrderListScreen extends StatefulWidget {
  static const String routeName = '/orders';
  const OrderListScreen({super.key});

  @override
  State<OrderListScreen> createState() => _OrderListScreenState();
}

class _OrderListScreenState extends State<OrderListScreen> {
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    // 화면이 처음 빌드될 때, OrderProvider의 orders 목록이 비어있으면 fetchUserOrders를 호출합니다.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final orderProvider = Provider.of<OrderProvider>(context, listen: false);
      // 화면에 처음 진입하거나, 주문 목록이 비어있고 로딩 중이 아닐 때 데이터 로드
      if (orderProvider.orders.isEmpty && !orderProvider.isLoading) {
        debugPrint("OrderListScreen initState: orders is empty and not loading, calling fetchUserOrders.");
        orderProvider.fetchUserOrders(); // 초기 페이지 로드
      } else {
        debugPrint("OrderListScreen initState: orders are already loaded or loading. Count: ${orderProvider.orders.length}");
      }
    });

    // 스크롤 리스너 추가 (무한 스크롤/페이지네이션)
    _scrollController.addListener(() {
      final orderProvider = context.read<OrderProvider>();
      // 스크롤이 거의 끝에 도달했고, 로딩 중이 아니며, 마지막 페이지가 아닐 때 다음 페이지 로드
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 200 && // 하단에서 200px 전에 로드 시작
          !orderProvider.isLoading &&
          !orderProvider.isLastPage) {
        debugPrint("OrderListScreen: Reached end of list, fetching more orders.");
        orderProvider.fetchUserOrders(loadMore: true); // 다음 페이지 로드
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  // 당겨서 새로고침 처리 함수
  Future<void> _onRefresh() async {
    debugPrint("OrderListScreen: RefreshIndicator onRefresh 호출됨");
    // Provider를 통해 주문 목록을 새로고침 (첫 페이지부터 다시 로드)
    await Provider.of<OrderProvider>(context, listen: false).refreshOrders();
  }

  @override
  Widget build(BuildContext context) {
    // OrderProvider를 watch하여 상태 변경 시 UI 자동 업데이트
    final orderProvider = Provider.of<OrderProvider>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('내 주문 내역'),
      ),
      body: RefreshIndicator(
        onRefresh: _onRefresh,
        child: Builder(
          builder: (context) {
            // 1. 초기 로딩 중 (orders가 비어있고 isLoading이 true)
            if (orderProvider.isLoading && orderProvider.orders.isEmpty) {
              debugPrint("OrderListScreen UI: 초기 로딩 중...");
              return const Center(child: CircularProgressIndicator());
            }
            // 2. 로딩 중 오류 발생 (orders가 비어있고 errorMessage가 있음)
            if (orderProvider.errorMessage != null && orderProvider.orders.isEmpty) {
              debugPrint("OrderListScreen UI: 오류 발생 - ${orderProvider.errorMessage}");
              return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 60),
                        const SizedBox(height: 16),
                        Text('주문 내역을 불러오는 중 오류가 발생했습니다:\n${orderProvider.errorMessage}', textAlign: TextAlign.center, style: TextStyle(fontSize: 16, color: Colors.red[700])),
                        const SizedBox(height: 20),
                        ElevatedButton.icon(
                          icon: const Icon(Icons.refresh_rounded),
                          label: const Text("다시 시도"),
                          onPressed: _onRefresh,
                        )
                      ],
                    ),
                  )
              );
            }
            // 3. 주문 내역이 없음
            if (orderProvider.orders.isEmpty) {
              debugPrint("OrderListScreen UI: 주문 내역 비어있음");
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.receipt_long_outlined, size: 100, color: Colors.grey[400]),
                    const SizedBox(height: 20),
                    const Text('아직 주문 내역이 없습니다.', style: TextStyle(fontSize: 18, color: Colors.grey)),
                    const SizedBox(height: 8),
                    Text('첫 주문을 시작해보세요!', style: TextStyle(fontSize: 16, color: Colors.grey[600])),
                  ],
                ),
              );
            }

            // 4. 주문 내역이 있는 경우
            debugPrint("OrderListScreen UI: 주문 내역 표시. 개수: ${orderProvider.orders.length}");
            return ListView.builder(
              controller: _scrollController,
              // 로딩 인디케이터 또는 "마지막 페이지" 메시지를 위한 추가 공간
              itemCount: orderProvider.orders.length + (orderProvider.isLastPage || !orderProvider.isLoading ? 0 : 1),
              itemBuilder: (context, index) {
                if (index == orderProvider.orders.length) {
                  // 로딩 중이고 마지막 페이지가 아닐 때만 로딩 인디케이터 표시
                  return orderProvider.isLoading && !orderProvider.isLastPage
                      ? const Padding(
                    padding: EdgeInsets.symmetric(vertical: 20.0),
                    child: Center(child: CircularProgressIndicator()),
                  )
                      : orderProvider.isLastPage && orderProvider.orders.isNotEmpty // 마지막 페이지이고 아이템이 있으면
                      ? const Padding(
                    padding: EdgeInsets.symmetric(vertical: 20.0),
                    child: Center(child: Text("더 이상 주문 내역이 없습니다.", style: TextStyle(color: Colors.grey))),
                  )
                      : const SizedBox.shrink();
                }
                final Order order = orderProvider.orders[index];
                return OrderCard(
                  order: order,
                  onTap: () {
                    // TODO: 주문 상세 화면으로 이동
                    // Navigator.of(context).pushNamed(OrderDetailScreen.routeName, arguments: order.orderId);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('주문 #${order.orderId} 상세 보기 (구현 필요)')),
                    );
                    // 주문 상세 정보 로드 (선택적, OrderDetailScreen에서 할 수도 있음)
                    // Provider.of<OrderProvider>(context, listen: false).fetchOrderDetail(order.orderId);
                  },
                );
              },
            );
          },
        ),
      ),
    );
  }
}
