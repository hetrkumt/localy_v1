// 파일 위치: lib/presentation/providers/cart_provider.dart
import 'package:flutter/foundation.dart';
import 'package:localy_front_flutter/data/models/cart_models.dart';
import 'package:localy_front_flutter/data/models/order_models.dart';
import 'package:localy_front_flutter/data/services/cart_api_service.dart';
import 'package:localy_front_flutter/data/services/order_api_service.dart';
import 'auth_provider.dart';

class CartProvider with ChangeNotifier {
  final CartApiService _cartApiService;
  final OrderApiService _orderApiService;
  AuthProvider _authProvider;

  Cart? _cart;
  bool _isLoading = false;
  String? _errorMessage;

  Cart? get cart => _cart;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  int get totalItems => _cart?.totalItems ?? 0;
  double get totalAmount => _cart?.totalAmount ?? 0.0;

  CartProvider(this._authProvider)
      : _cartApiService = CartApiService(apiClient: _authProvider.apiClient),
        _orderApiService = OrderApiService(apiClient: _authProvider.apiClient) {
    debugPrint("CartProvider: 생성자 호출 - Auth isAuthenticated: ${_authProvider.isAuthenticated}");
    // 생성자에서 바로 fetchCart를 호출하지 않고, 필요한 시점에 UI에서 호출하도록 변경
    // if (_authProvider.isAuthenticated) {
    //   debugPrint("CartProvider: 생성자 - 인증됨, fetchCart 호출 예정");
    //   fetchCart();
    // }
  }

  void update(AuthProvider authProvider) {
    bool wasAuthenticated = _authProvider.isAuthenticated;
    _authProvider = authProvider;
    debugPrint("CartProvider: update 호출 - newAuth isAuthenticated: ${authProvider.isAuthenticated}, wasAuthenticated: $wasAuthenticated");
    if (authProvider.isAuthenticated && !wasAuthenticated) {
      debugPrint("CartProvider: update - 로그인 상태로 변경됨, fetchCart 호출");
      fetchCart(); // 로그인 시에는 장바구니 가져오기
    } else if (!authProvider.isAuthenticated && wasAuthenticated) {
      debugPrint("CartProvider: update - 로그아웃 상태로 변경됨, 로컬 카트 초기화");
      _cart = null;
      _errorMessage = null;
      notifyListeners();
    }
  }

  Future<void> fetchCart({bool force = false}) async {
    // force가 true가 아니면, 이미 로드되었거나 로딩 중일 때 다시 호출하지 않음
    if (!force && (_isLoading || (_cart != null && _cart!.items.isNotEmpty))) {
      debugPrint("CartProvider: fetchCart - 이미 로드되었거나 로딩 중이므로 중단 (force: $force, isLoading: $_isLoading, cart items: ${_cart?.items.length})");
      // 만약 cart가 null이 아니지만 아이템이 없는 경우 (예: 서버에서 빈 카트 반환)에는 fetch를 허용할 수 있음
      if (!force && _cart != null && _cart!.items.isEmpty && !_isLoading) {
        debugPrint("CartProvider: fetchCart - 카트는 있지만 아이템이 없어 강제 fetch는 아니지만 진행");
      } else if (!force) {
        return;
      }
    }

    debugPrint("CartProvider: fetchCart 호출 시작 (force: $force) - Auth: ${_authProvider.isAuthenticated}, UserID: ${_authProvider.userId}");
    if (!_authProvider.isAuthenticated || _authProvider.userId == null) {
      _errorMessage = "장바구니를 보려면 로그인이 필요합니다.";
      _cart = null;
      _isLoading = false;
      notifyListeners();
      debugPrint("CartProvider: fetchCart - 인증되지 않았거나 userId가 없어 중단.");
      return;
    }
    _isLoading = true;
    _errorMessage = null;
    notifyListeners(); // 로딩 시작 알림

    try {
      final fetchedCart = await _cartApiService.getCart();
      _cart = fetchedCart;
      debugPrint("CartProvider: 장바구니 로드 완료. 아이템 수: ${_cart?.items.length ?? 0}, 가게 ID: ${_cart?.currentStoreId}");
    } catch (e) {
      _errorMessage = "장바구니 로드 실패: ${e.toString()}";
      _cart = null;
      debugPrint("CartProvider: fetchCart 오류 - $_errorMessage");
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> addItem(String menuId, String menuName, double unitPrice, int quantity, int storeId) async {
    debugPrint("CartProvider: addItem 호출 시작 - MenuId: $menuId, StoreId: $storeId");
    // ... (인증 및 다른 가게 상품 체크 로직은 동일) ...
    if (!_authProvider.isAuthenticated || _authProvider.userId == null) {
      _errorMessage = "로그인이 필요합니다.";
      notifyListeners();
      return;
    }
    if (_cart != null && _cart!.items.isNotEmpty && _cart!.currentStoreId != null && _cart!.currentStoreId != storeId) {
      _errorMessage = "다른 가게의 상품을 함께 담을 수 없습니다. 기존 장바구니를 비우고 다시 시도해주세요.";
      notifyListeners();
      return;
    }

    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final request = AddItemToCartRequest(
        menuId: menuId,
        menuName: menuName,
        quantity: quantity,
        unitPrice: unitPrice,
        storeId: storeId,
      );
      // CartApiService가 업데이트된 Cart를 반환하면, 그것으로 _cart 상태를 바로 업데이트
      _cart = await _cartApiService.addItemToCart(request);
      debugPrint("CartProvider: 상품 추가/수정 후 Cart 상태 업데이트 완료. 아이템 수: ${_cart?.items.length ?? 0}, 가게 ID: ${_cart?.currentStoreId}");
      // 여기서 fetchCart()를 다시 호출하지 않음
    } catch (e) {
      _errorMessage = "상품 추가 실패: ${e.toString()}";
      debugPrint("CartProvider: addItem 오류 - $_errorMessage");
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  // updateItemQuantity, removeItem, clearCart도 addItem과 유사하게
  // CartApiService 호출 후 반환된 Cart로 _cart를 업데이트하고 fetchCart()를 호출하지 않도록 합니다.
  // (이전 flutter_cart_provider_v5_final_updated 코드에서 이미 이렇게 되어 있습니다.)

  Future<void> updateItemQuantity(String menuId, int newQuantity) async {
    // ... (이전과 동일한 로직, fetchCart() 호출 없음) ...
    debugPrint("CartProvider: updateItemQuantity 호출 시작 - MenuId: $menuId, NewQty: $newQuantity");
    if (!_authProvider.isAuthenticated || _authProvider.userId == null) return;
    if (newQuantity <= 0) {
      await removeItem(menuId);
      return;
    }
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    try {
      _cart = await _cartApiService.updateItemQuantity(menuId, newQuantity);
      debugPrint("CartProvider: 수량 변경 후 Cart 상태 업데이트 완료 - ${menuId} (새 수량: ${_cart?.items[menuId]?.quantity}), 가게 ID: ${_cart?.currentStoreId}");
    } catch (e) {
      _errorMessage = "수량 변경 실패: ${e.toString()}";
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> removeItem(String menuId) async {
    // ... (이전과 동일한 로직, fetchCart() 호출 없음) ...
    debugPrint("CartProvider: removeItem 호출 시작 - MenuId: $menuId");
    if (!_authProvider.isAuthenticated || _authProvider.userId == null) return;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    try {
      _cart = await _cartApiService.removeItemFromCart(menuId);
      debugPrint("CartProvider: 상품 삭제 후 Cart 상태 업데이트 완료. 아이템 수: ${_cart?.items.length ?? 0}, 가게 ID: ${_cart?.currentStoreId}");
    } catch (e) {
      _errorMessage = "상품 삭제 실패: ${e.toString()}";
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> clearCart() async {
    // ... (이전과 동일한 로직, fetchCart() 호출 없음) ...
    debugPrint("CartProvider: clearCart 호출 시작");
    if (!_authProvider.isAuthenticated || _authProvider.userId == null) return;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    try {
      _cart = await _cartApiService.clearCart();
      debugPrint("CartProvider: 장바구니 비우기 후 Cart 상태 업데이트 완료. 아이템 수: ${_cart?.items.length ?? 0}, 가게 ID: ${_cart?.currentStoreId}");
    } catch (e) {
      _errorMessage = "장바구니 비우기 실패: ${e.toString()}";
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<Order?> checkout() async {
    // ... (이전 checkout 로직과 동일, 주문 성공 후 fetchCart() 호출은 유지) ...
    debugPrint("CartProvider: checkout 호출 시작");
    if (_cart == null || _cart!.items.isEmpty || !_authProvider.isAuthenticated || _authProvider.userId == null) {
      _errorMessage = "장바구니가 비어있거나 로그인이 필요합니다.";
      notifyListeners();
      debugPrint("CartProvider: checkout 중단 - ${_errorMessage}");
      return null;
    }
    if (_cart!.currentStoreId == null) {
      _errorMessage = "장바구니에 가게 정보가 없습니다. 상품을 다시 담아주세요.";
      notifyListeners();
      debugPrint("CartProvider: checkout 중단 - ${_errorMessage}");
      return null;
    }

    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final orderLineItems = _cart!.items.values.map((cartItem) =>
          CartItemDtoForOrder(
            menuId: cartItem.menuId,
            menuName: cartItem.menuName,
            quantity: cartItem.quantity,
            unitPrice: cartItem.unitPrice,
          )
      ).toList();

      final orderRequestData =
      CreateOrderRequestDtoForFlutter(
        storeId: _cart!.currentStoreId!.toString(),
        cartItems: orderLineItems,
      );

      final createdOrder = await _orderApiService.createOrder(orderRequestData.toJson());

      debugPrint("CartProvider: 주문 생성 성공! Order ID: ${createdOrder.orderId}");
      await fetchCart(force: true); // 주문 성공 후에는 장바구니가 비워지므로, 강제로 서버에서 다시 가져옴
      debugPrint("CartProvider: checkout 후 fetchCart 완료. 현재 아이템 수: ${_cart?.items.length ?? 0}");
      return createdOrder;
    } catch (e) {
      _errorMessage = "주문 처리 중 오류가 발생했습니다: ${e.toString()}";
      debugPrint("CartProvider: checkout 오류 - $_errorMessage");
      notifyListeners();
      return null;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
