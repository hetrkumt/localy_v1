// 파일 위치: lib/presentation/screens/home/home_screen.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
import '../../../data/models/store_models.dart';
import '../../../data/services/store_api_service.dart';
import '../../../data/services/auth_api_service.dart';
import '../auth/login_screen.dart';
import '../cart/cart_screen.dart';

class HomeScreen extends StatefulWidget {
  static const String routeName = '/home';
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final StoreApiService _storeApiService = StoreApiService();
  final AuthApiService _authApiService = AuthApiService();

  Future<List<Store>>? _storesFuture;
  final Completer<NaverMapController> _mapControllerCompleter = Completer();
  Set<NMarker> _currentMarkers = {};
  List<Store> _loadedStores = [];

  NCameraPosition _currentCameraPosition = const NCameraPosition(
    target: NLatLng(37.5666102, 126.9783881), // 예시: 서울 시청
    zoom: 12,
  );

  @override
  void initState() {
    super.initState();
    _loadInitialData();
  }

  void _loadInitialData() {
    if (mounted) {
      setState(() {
        _storesFuture = _fetchStoresAndSetupMap();
      });
    }
  }

  Future<List<Store>> _fetchStoresAndSetupMap() async {
    try {
      debugPrint("HomeScreen: 가게 목록 로딩 시작...");
      final stores = await _storeApiService.getAllStores();
      _loadedStores = stores;
      debugPrint("HomeScreen: ${stores.length}개의 가게 정보 로드 완료.");
      await _updateMarkersOnMap(stores);

      if (stores.isNotEmpty) {
        final firstStoreWithLocation = stores.firstWhere(
              (store) => store.latitude != null && store.longitude != null,
          orElse: () => stores.first,
        );
        if (firstStoreWithLocation.latitude != null && firstStoreWithLocation.longitude != null) {
          _moveCameraToPosition(
            NLatLng(firstStoreWithLocation.latitude!, firstStoreWithLocation.longitude!),
            zoom: 14,
          );
        }
      }
      return stores;
    } catch (e) {
      debugPrint('HomeScreen: 가게 목록 로딩 중 심각한 오류 발생: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('가게 정보를 불러오는데 실패했습니다. 네트워크 연결을 확인해주세요. 오류: ${e.toString().substring(0, (e.toString().length > 60 ? 60 : e.toString().length))}...')),
        );
      }
      return [];
    }
  }

  // NMarker의 setOnTapListener에 전달될 콜백 함수
  // 시그니처: void Function(NMarker marker)
  void _handleMarkerTap(NMarker tappedMarker) { // iconSize 파라미터 제거
    debugPrint("마커 탭됨: ID - ${tappedMarker.info.id}");
    try {
      final store = _loadedStores.firstWhere(
            (s) => s.id.toString() == tappedMarker.info.id,
      );
      _displayStoreInfoBottomSheet(store);
    } catch (e) {
      debugPrint("탭된 마커에 해당하는 가게 정보를 _loadedStores에서 찾을 수 없습니다. Marker ID: ${tappedMarker.info.id}, 오류: $e");
    }
  }

  Future<void> _updateMarkersOnMap(List<Store> stores) async {
    final Set<NMarker> newMarkers = {};
    for (final store in stores) {
      if (store.latitude != null && store.longitude != null) {
        final marker = NMarker( // 생성자에는 onTap 관련 파라미터 없음 (문서 기준)
          id: store.id.toString(),
          position: NLatLng(store.latitude!, store.longitude!),
          caption: NOverlayCaption(text: store.name, minZoom: 10),
        );
        // *** 수정된 부분: 생성된 마커 객체에 setOnTapListener 메서드 호출 ***
        marker.setOnTapListener((NMarker tappedMarkerParam) { // 콜백 시그니처에 맞게 수정
          _handleMarkerTap(tappedMarkerParam);
        });
        newMarkers.add(marker);
      }
    }

    if (mounted) {
      setState(() {
        _currentMarkers = newMarkers;
      });

      if (_mapControllerCompleter.isCompleted) {
        final mapController = await _mapControllerCompleter.future;
        mapController.clearOverlays(type: NOverlayType.marker);
        if (newMarkers.isNotEmpty) {
          mapController.addOverlayAll(newMarkers);
        }
        debugPrint("${newMarkers.length}개의 마커가 지도에 업데이트되었습니다.");
      }
    }
  }

  void _displayStoreInfoBottomSheet(Store store) {
    // ... (BottomSheet UI 코드는 이전과 동일하게 유지) ...
    debugPrint('가게 탭됨 (UI 표시): ${store.name} (ID: ${store.id})');
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (BuildContext context) {
        return DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.4,
          minChildSize: 0.2,
          maxChildSize: 0.65,
          builder: (BuildContext context, ScrollController scrollController) {
            return Container(
              padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 20.0),
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.grey.withOpacity(0.3),
                    spreadRadius: 1,
                    blurRadius: 8,
                    offset: const Offset(0, -2),
                  ),
                ],
              ),
              child: ListView(
                controller: scrollController,
                children: <Widget>[
                  Center(
                    child: Container(
                      width: 40,
                      height: 5,
                      margin: const EdgeInsets.only(bottom: 10),
                      decoration: BoxDecoration(
                        color: Colors.grey[300],
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                  Text(store.name, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  if (store.address != null) ListTile(leading: Icon(Icons.location_on_outlined, color: Theme.of(context).primaryColor), title: Text(store.address!), dense: true),
                  if (store.phone != null) ListTile(leading: Icon(Icons.phone_outlined, color: Theme.of(context).primaryColor), title: Text(store.phone!), dense: true),
                  if (store.openingHours != null) ListTile(leading: Icon(Icons.access_time_outlined, color: Theme.of(context).primaryColor), title: Text(store.openingHours!), dense: true),
                  ListTile(leading: Icon(Icons.category_outlined, color: Theme.of(context).primaryColor), title: Text('카테고리: ${storeCategoryToString(store.category)}'), dense: true),
                  ListTile(leading: Icon(Icons.info_outline, color: Theme.of(context).primaryColor), title: Text('상태: ${storeStatusToString(store.status)}'), dense: true),
                  const SizedBox(height: 20),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.restaurant_menu),
                    label: const Text('가게 메뉴 보기 / 주문하기'),
                    onPressed: () {
                      Navigator.pop(context);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('${store.name} 메뉴 화면으로 이동합니다 (구현 필요).')),
                      );
                    },
                    style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 48)),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _moveCameraToPosition(NLatLng position, {double zoom = 15}) async {
    // ... (이전과 동일) ...
    if (!mounted) return;
    if (_mapControllerCompleter.isCompleted) {
      final mapController = await _mapControllerCompleter.future;
      mapController.updateCamera(
        NCameraUpdate.scrollAndZoomTo(target: position, zoom: zoom),
      );
    } else {
      setState(() {
        _currentCameraPosition = NCameraPosition(target: position, zoom: zoom);
      });
    }
  }

  Future<void> _handleLogout() async {
    // ... (이전과 동일) ...
    try {
      await _authApiService.logout();
      if (mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (context) => const LoginScreen()),
              (Route<dynamic> route) => false,
        );
      }
    } catch (e) {
      debugPrint("HomeScreen: 로그아웃 실패 - $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('로그아웃 중 오류가 발생했습니다: ${e.toString()}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Localy - 내 주변 가게'),
        actions: [
          IconButton(
            icon: const Icon(Icons.shopping_cart_outlined),
            tooltip: '장바구니',
            onPressed: () {
              Navigator.of(context).push(MaterialPageRoute(builder: (context) => const CartScreen()));
            },
          ),
          IconButton(
            icon: const Icon(Icons.logout_outlined),
            tooltip: '로그아웃',
            onPressed: _handleLogout,
          ),
        ],
      ),
      body: FutureBuilder<List<Store>>(
        future: _storesFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, color: Colors.redAccent, size: 60),
                      const SizedBox(height: 16),
                      Text('데이터 로딩 오류:\n${snapshot.error}', textAlign: TextAlign.center, style: TextStyle(color: Colors.red[700])),
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        icon: const Icon(Icons.refresh),
                        label: const Text("다시 시도"),
                        onPressed: _loadInitialData,
                      )
                    ],
                  ),
                )
            );
          }
          if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // *** 수정된 부분: const Icon 사용 시 Colors.grey.shade400 사용 ***
                  Icon(Icons.storefront_outlined, size: 80, color: Colors.grey.shade400),
                  const SizedBox(height: 16),
                  const Text('주변에 가게 정보가 없습니다.', style: TextStyle(fontSize: 18, color: Colors.grey)),
                  const SizedBox(height: 20),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.refresh),
                    label: const Text("새로고침"),
                    onPressed: _loadInitialData,
                  )
                ],
              ),
            );
          }
          return NaverMap(
            options: NaverMapViewOptions(
              initialCameraPosition: _currentCameraPosition,
              locationButtonEnable: true,
            ),
            onMapReady: (controller) async {
              debugPrint("네이버맵 로딩 완료! (HomeScreen)");
              if (!_mapControllerCompleter.isCompleted) {
                _mapControllerCompleter.complete(controller);
              }
              if (_currentMarkers.isNotEmpty) {
                controller.addOverlayAll(_currentMarkers);
              }
            },
            onMapTapped: (point, latLng) {
              debugPrint("지도 탭: ${latLng.latitude}, ${latLng.longitude}");
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _loadInitialData,
        tooltip: '가게 목록 새로고침',
        child: const Icon(Icons.refresh),
      ),
    );
  }
}
