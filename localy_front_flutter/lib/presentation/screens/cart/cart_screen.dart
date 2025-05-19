// 파일 위치: lib/presentation/screens/cart/cart_screen.dart
import 'package:flutter/material.dart';
import '../../../data/models/cart_models.dart';
import '../../../data/services/cart_api_service.dart';
// import '../order/order_confirm_screen.dart'; // 주문 확인 또는 완료 화면 (구현 필요)

class CartScreen extends StatefulWidget {
  static const String routeName = '/cart';
  const CartScreen({super.key});

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  final CartApiService _cartApiService = CartApiService(); // 실제로는 DI 사용 권장
  Future<Cart?>? _cartFuture;
  // Future<double>? _totalFuture; // Cart 모델 내에 totalCartPrice 게터를 사용

  bool _isProcessing = false; // 중복 요청 방지를 위한 플래그

  @override
  void initState() {
    super.initState();
    _loadCartData();
  }

  void _loadCartData() {
    if (mounted) {
      setState(() {
        _cartFuture = _cartApiService.getCart();
        // _totalFuture = _cartApiService.calculateTotal(); // getCart() 후 Cart 모델에서 계산
      });
    }
  }

  Future<void> _updateQuantity(String menuId, int currentQuantity, int change) async {
    if (_isProcessing) return;
    final newQuantity = currentQuantity + change;
    if (newQuantity <= 0) {
      await _removeItem(menuId);
      return;
    }
    setState(() { _isProcessing = true; });
    try {
      await _cartApiService.updateItemQuantity(menuId, newQuantity);
      _loadCartData();
    } catch (e) {
      debugPrint("수량 업데이트 실패: $e");
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("수량 변경 실패: ${e.toString().substring(0, (e.toString().length > 80 ? 80 : e.toString().length))}...")));
    } finally {
      if (mounted) setState(() { _isProcessing = false; });
    }
  }

  Future<void> _removeItem(String menuId) async {
    if (_isProcessing) return;
    setState(() { _isProcessing = true; });
    try {
      await _cartApiService.removeItemFromCart(menuId);
      _loadCartData(); // 장바구니 정보 새로고침
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("상품이 장바구니에서 삭제되었습니다.")));
    } catch (e) {
      debugPrint("상품 삭제 실패: $e");
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("상품 삭제 실패: ${e.toString().substring(0, (e.toString().length > 80 ? 80 : e.toString().length))}...")));
    } finally {
      if (mounted) setState(() { _isProcessing = false; });
    }
  }

  Future<void> _clearCartDialog() async {
    if (_isProcessing) return;
    final cart = await _cartFuture;
    if (cart == null || cart.cartItems.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("장바구니가 이미 비어있습니다.")));
      return;
    }

    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('장바구니 비우기'),
        content: const Text('장바구니에 담긴 모든 상품을 삭제하시겠습니까?'),
        actions: <Widget>[
          TextButton(
            child: const Text('취소'),
            onPressed: () => Navigator.of(ctx).pop(false),
          ),
          TextButton(
            child: const Text('확인', style: TextStyle(color: Colors.red)),
            onPressed: () => Navigator.of(ctx).pop(true),
          ),
        ],
      ),
    );

    if (confirm == true) {
      setState(() { _isProcessing = true; });
      try {
        await _cartApiService.clearCart();
        _loadCartData();
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("장바구니를 모두 비웠습니다.")));
      } catch (e) {
        debugPrint("장바구니 비우기 실패: $e");
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("장바구니 비우기 실패: ${e.toString().substring(0, (e.toString().length > 80 ? 80 : e.toString().length))}...")));
      } finally {
        if (mounted) setState(() { _isProcessing = false; });
      }
    }
  }

  Future<void> _checkout() async {
    if (_isProcessing) return;
    final cart = await _cartFuture; // 현재 장바구니 상태 확인
    if (cart == null || cart.cartItems.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("장바구니가 비어있어 주문할 수 없습니다.")));
      return;
    }

    setState(() { _isProcessing = true; });
    try {
      final result = await _cartApiService.checkout();
      if (result.success) {
        debugPrint("주문 성공! 주문 ID: ${result.orderId}");
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text("주문이 성공적으로 완료되었습니다. (주문 ID: ${result.orderId})"),
              backgroundColor: Colors.green,
            ),
          );
          // TODO: 주문 완료 화면으로 이동하거나, 주문 내역 화면을 업데이트하거나, 홈으로 이동
          // Navigator.of(context).pushReplacementNamed(OrderConfirmationScreen.routeName, arguments: result.orderId);
          _loadCartData(); // 장바구니가 비워졌는지 확인
        }
      } else {
        debugPrint("주문 실패: ${result.errorMessage}");
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text("주문 실패: ${result.errorMessage ?? '알 수 없는 오류'}"),
              backgroundColor: Colors.redAccent,
            ),
          );
        }
      }
    } catch (e) {
      debugPrint("체크아웃 중 오류: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("주문 처리 중 오류가 발생했습니다: ${e.toString().substring(0, (e.toString().length > 80 ? 80 : e.toString().length))}..."),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() { _isProcessing = false; });
    }
  }


  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('내 장바구니'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_sweep_outlined),
            tooltip: '장바구니 비우기',
            onPressed: _isProcessing ? null : _clearCartDialog,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          _loadCartData();
        },
        child: FutureBuilder<Cart?>(
          future: _cartFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting && !_isProcessing) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text('장바구니를 불러오는 중 오류가 발생했습니다.\n${snapshot.error}', textAlign: TextAlign.center),
                  )
              );
            }
            if (!snapshot.hasData || snapshot.data == null || snapshot.data!.cartItems.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.shopping_cart_outlined, size: 80, color: Colors.grey[400]),
                    const SizedBox(height: 16),
                    const Text('장바구니가 비어있습니다.', style: TextStyle(fontSize: 18, color: Colors.grey)),
                    const SizedBox(height: 8),
                    const Text('마음에 드는 상품을 담아보세요!', style: TextStyle(color: Colors.grey)),
                  ],
                ),
              );
            }

            final cart = snapshot.data!;
            final items = cart.cartItems.values.toList();

            return Column(
              children: [
                if (cart.storeId != null) // 장바구니에 담긴 가게 정보 표시 (선택 사항)
                  Padding(
                    padding: const EdgeInsets.all(12.0),
                    child: Text("현재 '${cart.storeId}' 가게의 상품만 담겨있습니다.", // TODO: storeId로 가게 이름 조회
                        style: TextStyle(fontSize: 14, color: Colors.grey[700])),
                  ),
                Expanded(
                  child: ListView.separated(
                    itemCount: items.length,
                    itemBuilder: (ctx, index) {
                      final item = items[index];
                      return ListTile(
                        leading: CircleAvatar(
                          backgroundColor: Theme.of(context).primaryColorLight,
                          child: Text('${item.quantity}', style: TextStyle(color: Theme.of(context).primaryColorDark)),
                        ),
                        title: Text(item.menuName, style: const TextStyle(fontWeight: FontWeight.w500)),
                        subtitle: Text('개당 ${item.unitPrice.toStringAsFixed(0)}원\n합계: ${(item.unitPrice * item.quantity).toStringAsFixed(0)}원'),
                        isThreeLine: true,
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: Icon(Icons.remove_circle_outline, color: Colors.redAccent[100]),
                              tooltip: '수량 줄이기',
                              onPressed: _isProcessing ? null : () => _updateQuantity(item.menuId, item.quantity, -1),
                            ),
                            IconButton(
                              icon: Icon(Icons.add_circle_outline, color: Colors.green[300]),
                              tooltip: '수량 늘리기',
                              onPressed: _isProcessing ? null : () => _updateQuantity(item.menuId, item.quantity, 1),
                            ),
                            IconButton(
                              icon: Icon(Icons.delete_forever_outlined, color: Colors.grey[600]),
                              tooltip: '상품 삭제',
                              onPressed: _isProcessing ? null : () => _removeItem(item.menuId),
                            ),
                          ],
                        ),
                      );
                    },
                    separatorBuilder: (ctx, index) => const Divider(height: 1, indent: 16, endIndent: 16),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.all(16.0),
                  decoration: BoxDecoration(
                      color: Colors.white,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.grey.withOpacity(0.2),
                          spreadRadius: 0,
                          blurRadius: 10,
                          offset: const Offset(0, -5), // changes position of shadow
                        ),
                      ],
                      borderRadius: const BorderRadius.vertical(top: Radius.circular(16))
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('총 상품 금액:', style: TextStyle(fontSize: 18)),
                          Text(
                            '${cart.totalCartPrice.toStringAsFixed(0)}원',
                            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: (items.isEmpty || _isProcessing) ? null : _checkout,
                        style: ElevatedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        child: _isProcessing
                            ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 3))
                            : const Text('주문하기'),
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
