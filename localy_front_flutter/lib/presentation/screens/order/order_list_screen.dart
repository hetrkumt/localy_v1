// 파일 위치: lib/presentation/screens/order/order_list_screen.dart
import 'package:flutter/material.dart';
import 'package:localy_front_flutter/presentation/providers/order_provider.dart';
import 'package:localy_front_flutter/presentation/widgets/order_card.dart'; // OrderCard 임포트
import 'package:provider/provider.dart';
// import 'order_detail_screen.dart'; // 주문 상세 화면 (구현 필요)

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
    // 화면 진입 시 첫 주문 목록 로드
    Future.microtask(() =>
        Provider.of<OrderProvider>(context, listen: false).fetchUserOrders());

    _scrollController.addListener(() {
      final orderProvider = context.read<OrderProvider>();
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 200 &&
          !orderProvider.isLoading &&
          !orderProvider.isLastPage) {
        orderProvider.fetchUserOrders(loadMore: true);
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _onRefresh() async {
    await Provider.of<OrderProvider>(context, listen: false).refreshOrders();
  }

  @override
  Widget build(BuildContext context) {
    final orderProvider = Provider.of<OrderProvider>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('내 주문 내역'),
      ),
      body: RefreshIndicator(
        onRefresh: _onRefresh,
        child: Builder(
          builder: (context) {
            if (orderProvider.isLoading && orderProvider.orders.isEmpty) {
              return const Center(child: CircularProgressIndicator());
            }
            if (orderProvider.errorMessage != null && orderProvider.orders.isEmpty) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, color: Colors.red, size: 50),
                      const SizedBox(height: 10),
                      Text('주문 내역을 불러오는 중 오류:\n${orderProvider.errorMessage}', textAlign: TextAlign.center),
                      const SizedBox(height: 10),
                      ElevatedButton(onPressed: _onRefresh, child: const Text('다시 시도')),
                    ],
                  ),
                ),
              );
            }
            if (orderProvider.orders.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.receipt_long_outlined, size: 80, color: Colors.grey[400]),
                    const SizedBox(height: 16),
                    const Text('아직 주문 내역이 없습니다.', style: TextStyle(fontSize: 18, color: Colors.grey)),
                  ],
                ),
              );
            }

            return ListView.builder(
              controller: _scrollController,
              itemCount: orderProvider.orders.length + (orderProvider.isLastPage ? 0 : 1),
              itemBuilder: (context, index) {
                if (index == orderProvider.orders.length) {
                  return orderProvider.isLoading
                      ? const Padding(
                    padding: EdgeInsets.all(16.0),
                    child: Center(child: CircularProgressIndicator()),
                  )
                      : const SizedBox.shrink();
                }
                final order = orderProvider.orders[index];
                return OrderCard(
                  order: order,
                  onTap: () {
                    // TODO: 주문 상세 화면으로 이동
                    // Navigator.of(context).pushNamed(OrderDetailScreen.routeName, arguments: order.orderId);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('주문 #${order.orderId} 상세 보기 (구현 필요)')),
                    );
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
