// 파일 위치: lib/presentation/screens/home/home_screen.dart
import 'dart:async'; // Completer, Timer 사용을 위해
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
import 'package:localy_front_flutter/data/models/store_models.dart';
import 'package:localy_front_flutter/presentation/providers/auth_provider.dart';
import 'package:localy_front_flutter/presentation/providers/store_provider.dart';
import 'package:localy_front_flutter/presentation/screens/auth/login_screen.dart';
import 'package:localy_front_flutter/presentation/screens/cart/cart_screen.dart';
import 'package:localy_front_flutter/presentation/screens/order/order_list_screen.dart';
import 'package:localy_front_flutter/presentation/screens/store/store_detail_screen.dart';
import 'package:localy_front_flutter/presentation/widgets/store_card.dart';
import 'package:provider/provider.dart';

class HomeScreen extends StatefulWidget {
  static const String routeName = '/home';
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Completer<NaverMapController> _mapControllerCompleter = Completer();
  final ScrollController _scrollController = ScrollController();

  bool _isListView = true;
  String? _selectedCategory;
  StoreSortOption _selectedSortOption = StoreSortOption.createdAtDesc;
  final TextEditingController _searchController = TextEditingController();
  Timer? _debounce;

  NCameraPosition _currentCameraPosition = const NCameraPosition(
    target: NLatLng(37.5666102, 126.9783881),
    zoom: 12,
  );
  // _currentMarkers는 StoreProvider의 stores가 변경될 때 _updateMarkersOnMap에 의해 업데이트됨
  // Set<NMarker> _currentMarkers = {}; // 직접 관리보다는 Provider 데이터 기반으로 생성

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadInitialStores();
      // Provider 리스너는 build 메서드에서 Consumer나 Selector를 사용하거나,
      // 정말 필요하다면 여기서 addListener를 사용할 수 있지만, 마커 업데이트는 지도 준비 후 또는 뷰 전환 시점에 하는 것이 더 적절.
      // _setupMapMarkersListener();
    });

    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 300 &&
          !context.read<StoreProvider>().isLoading &&
          !context.read<StoreProvider>().isLastPage) {
        context.read<StoreProvider>().fetchStores(
          name: context.read<StoreProvider>().currentSearchName,
          category: context.read<StoreProvider>().currentSearchCategory,
          menuKeyword: context.read<StoreProvider>().currentSearchMenuKeyword,
          sortOption: context.read<StoreProvider>().currentSortOption,
          loadMore: true,
        );
      }
    });
  }

  // void _setupMapMarkersListener() {
  //   final storeProvider = Provider.of<StoreProvider>(context, listen: false);
  //   storeProvider.addListener(_updateMarkersFromProvider);
  // }

  // void _updateMarkersFromProvider() {
  //   if (!mounted) return;
  //   final storeProvider = Provider.of<StoreProvider>(context, listen: false);
  //   _updateMarkersOnMap(storeProvider.stores);
  // }

  Future<void> _loadInitialStores() async {
    final storeProvider = Provider.of<StoreProvider>(context, listen: false);
    storeProvider.updateSearchName(_searchController.text.isNotEmpty ? _searchController.text : null);
    storeProvider.updateSearchCategory(_selectedCategory);
    storeProvider.updateSortOption(_selectedSortOption);
    await storeProvider.fetchStores(
        name: storeProvider.currentSearchName,
        category: storeProvider.currentSearchCategory,
        menuKeyword: storeProvider.currentSearchMenuKeyword,
        sortOption: storeProvider.currentSortOption);
    // _moveCameraToFirstStore(storeProvider.stores); // 필요시 활성화
  }

  Future<void> _onRefresh() async {
    final storeProvider = Provider.of<StoreProvider>(context, listen: false);
    await storeProvider.refreshStores();
    // _moveCameraToFirstStore(storeProvider.stores); // 필요시 활성화
  }

  void _onSearchChanged(String query) {
    if (_debounce?.isActive ?? false) _debounce!.cancel();
    _debounce = Timer(const Duration(milliseconds: 700), () {
      final storeProvider = Provider.of<StoreProvider>(context, listen: false);
      storeProvider.updateSearchName(query.isNotEmpty ? query : null);
      storeProvider.updateSearchMenuKeyword(query.isNotEmpty ? query : null);
      storeProvider.fetchStores(
        name: storeProvider.currentSearchName,
        menuKeyword: storeProvider.currentSearchMenuKeyword,
        category: storeProvider.currentSearchCategory,
        sortOption: storeProvider.currentSortOption,
      );
    });
  }

  Future<void> _updateMarkersOnMap(List<Store> stores) async {
    if (!_mapControllerCompleter.isCompleted || !mounted) return;

    final mapController = await _mapControllerCompleter.future;
    final Set<NMarker> newMarkers = {};
    for (final store in stores) {
      if (store.latitude != null && store.longitude != null) {
        final marker = NMarker(
          id: store.id.toString(),
          position: NLatLng(store.latitude!, store.longitude!),
          caption: NOverlayCaption(text: store.name, minZoom: 10),
        );
        marker.setOnTapListener((NMarker tappedMarkerParam) {
          _handleMarkerTap(tappedMarkerParam, stores);
        });
        newMarkers.add(marker);
      }
    }

    mapController.clearOverlays(type: NOverlayType.marker);
    if (newMarkers.isNotEmpty) {
      mapController.addOverlayAll(newMarkers);
      debugPrint("${newMarkers.length}개의 마커가 지도에 업데이트되었습니다.");
    }
  }

  void _handleMarkerTap(NMarker tappedMarker, List<Store> currentStores) {
    try {
      final store = currentStores.firstWhere(
            (s) => s.id.toString() == tappedMarker.info.id,
      );
      _displayStoreInfoBottomSheet(store);
    } catch (e) {
      debugPrint("탭된 마커에 해당하는 가게 정보를 찾을 수 없습니다. Marker ID: ${tappedMarker.info.id}, 오류: $e");
    }
  }

  void _displayStoreInfoBottomSheet(Store store) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (BuildContext context) {
        return DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.45,
          minChildSize: 0.2,
          maxChildSize: 0.7,
          builder: (BuildContext context, ScrollController scrollController) {
            return Container(
              padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 20.0),
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
                boxShadow: [ BoxShadow(color: Colors.grey.withOpacity(0.3), spreadRadius: 1, blurRadius: 8, offset: const Offset(0, -2)) ],
              ),
              child: ListView(
                controller: scrollController,
                children: <Widget>[
                  Center(child: Container(width: 40, height: 5, margin: const EdgeInsets.only(bottom: 12), decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(10)))),
                  Text(store.name, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Row(children: [
                    Icon(Icons.star, color: Colors.amber[600], size: 18), const SizedBox(width: 4),
                    Text(store.averageRating?.toStringAsFixed(1) ?? 'N/A', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                    const SizedBox(width: 6),
                    Text('(${store.reviewCount ?? 0} 리뷰)', style: TextStyle(fontSize: 13, color: Colors.grey[600])),
                  ]),
                  const SizedBox(height: 12),
                  if (store.address != null) ListTile(leading: Icon(Icons.location_on_outlined, color: Theme.of(context).primaryColor), title: Text(store.address!), dense: true, contentPadding: EdgeInsets.zero),
                  if (store.phone != null) ListTile(leading: Icon(Icons.phone_outlined, color: Theme.of(context).primaryColor), title: Text(store.phone!), dense: true, contentPadding: EdgeInsets.zero),
                  if (store.openingHours != null) ListTile(leading: Icon(Icons.access_time_outlined, color: Theme.of(context).primaryColor), title: Text(store.openingHours!), dense: true, contentPadding: EdgeInsets.zero),
                  ListTile(leading: Icon(Icons.category_outlined, color: Theme.of(context).primaryColor), title: Text('카테고리: ${storeCategoryToString(store.category)}'), dense: true, contentPadding: EdgeInsets.zero),
                  const SizedBox(height: 20),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.store_mall_directory_outlined),
                    label: const Text('가게 상세 보기'),
                    onPressed: () {
                      Navigator.pop(context);
                      Navigator.of(context).pushNamed(StoreDetailScreen.routeName, arguments: store.id);
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
    if (!mounted || !_mapControllerCompleter.isCompleted) return;
    final mapController = await _mapControllerCompleter.future;
    mapController.updateCamera(
      NCameraUpdate.scrollAndZoomTo(target: position, zoom: zoom),
    );
  }

  // void _moveCameraToFirstStore(List<Store> stores) { // 사용되지 않는다면 제거 가능
  //   if (stores.isNotEmpty) {
  //     final firstStoreWithLocation = stores.firstWhere(
  //       (store) => store.latitude != null && store.longitude != null,
  //       orElse: () => stores.first,
  //     );
  //     if (firstStoreWithLocation.latitude != null && firstStoreWithLocation.longitude != null) {
  //       _moveCameraToPosition(
  //         NLatLng(firstStoreWithLocation.latitude!, firstStoreWithLocation.longitude!),
  //         zoom: 14,
  //       );
  //     }
  //   }
  // }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    _debounce?.cancel();
    // Provider.of<StoreProvider>(context, listen: false).removeListener(_updateMarkersFromProvider); // 리스너 사용 시 제거
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context, listen: false);
    final storeProvider = Provider.of<StoreProvider>(context); // UI 업데이트를 위해 listen:true (기본값)

    // 지도 보기 모드일 때, storeProvider.stores가 변경되면 마커를 업데이트합니다.
    // 이 로직은 _buildMapView의 onMapReady에서도 수행되지만,
    // 필터링/검색 등으로 stores가 변경된 후 지도가 이미 ready 상태일 때를 위함입니다.
    if (!_isListView && _mapControllerCompleter.isCompleted) {
      WidgetsBinding.instance.addPostFrameCallback((_) { // 현재 빌드 사이클 이후에 실행
        if (mounted) { // 위젯이 여전히 마운트되어 있는지 확인
          _updateMarkersOnMap(storeProvider.stores);
        }
      });
    }


    return Scaffold(
      appBar: AppBar(
        title: const Text('Localy - 내 주변 가게'),
        actions: [
          IconButton(
            icon: Icon(_isListView ? Icons.map_outlined : Icons.view_list_outlined),
            tooltip: _isListView ? '지도 보기' : '목록 보기',
            onPressed: () {
              setState(() {
                _isListView = !_isListView;
                if (!_isListView) {
                  // 지도 보기로 전환될 때, 현재 storeProvider의 가게들로 마커를 즉시 업데이트 시도
                  // 지도가 준비된 후에 마커가 그려지도록 _buildMapView의 onMapReady에서 처리하는 것이 더 안정적일 수 있음
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted) _updateMarkersOnMap(storeProvider.stores);
                  });
                }
              });
            },
          ),
          IconButton(
            icon: const Icon(Icons.logout_outlined),
            tooltip: '로그아웃',
            onPressed: () async {
              await authProvider.logout();
              Navigator.of(context).pushNamedAndRemoveUntil(LoginScreen.routeName, (Route<dynamic> route) => false);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          _buildSearchAndFilterBar(storeProvider),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _onRefresh,
              child: _isListView
                  ? _buildStoreList(storeProvider)
                  : _buildMapView(storeProvider),
            ),
          ),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        currentIndex: 0,
        onTap: (index) {
          if (index == 2) {
            Navigator.of(context).pushNamed(CartScreen.routeName);
          } else if (index == 3) {
            Navigator.of(context).pushNamed(OrderListScreen.routeName);
          }
        },
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: '홈'),
          BottomNavigationBarItem(icon: Icon(Icons.search), label: '검색'),
          BottomNavigationBarItem(icon: Icon(Icons.shopping_cart), label: '장바구니'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt_long), label: '주문내역'),
        ],
      ),
    );
  }

  Widget _buildSearchAndFilterBar(StoreProvider storeProvider) {
    // ... (이전 코드와 동일) ...
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        children: [
          TextField(
            controller: _searchController,
            decoration: InputDecoration(
              hintText: '가게 또는 메뉴 검색...',
              prefixIcon: const Icon(Icons.search),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8.0), borderSide: BorderSide.none),
              filled: true,
              fillColor: Colors.grey[200],
              contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
            ),
            onChanged: _onSearchChanged,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  decoration: InputDecoration(labelText: '카테고리', border: OutlineInputBorder(borderRadius: BorderRadius.circular(8.0)), contentPadding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 8.0)),
                  value: _selectedCategory,
                  hint: const Text('전체'),
                  isExpanded: true,
                  items: [
                    const DropdownMenuItem<String>(value: null, child: Text('전체')),
                    ...StoreCategory.values.where((cat) => cat != StoreCategory.UNKNOWN).map((StoreCategory category) {
                      return DropdownMenuItem<String>(
                        value: storeCategoryToString(category),
                        child: Text(category.name), // TODO: 한글화
                      );
                    }).toList(),
                  ],
                  onChanged: (String? newValue) {
                    setState(() { _selectedCategory = newValue; });
                    storeProvider.updateSearchCategory(newValue);
                    storeProvider.fetchStores(
                      name: _searchController.text.isNotEmpty ? _searchController.text : null,
                      menuKeyword: storeProvider.currentSearchMenuKeyword,
                      category: newValue,
                      sortOption: _selectedSortOption,
                    );
                  },
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: DropdownButtonFormField<StoreSortOption>(
                  decoration: InputDecoration(labelText: '정렬', border: OutlineInputBorder(borderRadius: BorderRadius.circular(8.0)), contentPadding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 8.0)),
                  value: _selectedSortOption,
                  isExpanded: true,
                  items: StoreSortOption.values.map((StoreSortOption option) {
                    String optionText = '';
                    switch (option) {
                      case StoreSortOption.nameAsc: optionText = '이름↑'; break;
                      case StoreSortOption.nameDesc: optionText = '이름↓'; break;
                      case StoreSortOption.ratingAsc: optionText = '평점↑'; break;
                      case StoreSortOption.ratingDesc: optionText = '평점↓'; break;
                      case StoreSortOption.reviewCountAsc: optionText = '리뷰수↑'; break;
                      case StoreSortOption.reviewCountDesc: optionText = '리뷰수↓'; break;
                      case StoreSortOption.createdAtAsc: optionText = '오래된순'; break;
                      case StoreSortOption.createdAtDesc: optionText = '최신순'; break;
                    }
                    return DropdownMenuItem<StoreSortOption>(value: option, child: Text(optionText));
                  }).toList(),
                  onChanged: (StoreSortOption? newValue) {
                    if (newValue != null) {
                      setState(() { _selectedSortOption = newValue; });
                      storeProvider.updateSortOption(newValue);
                      storeProvider.fetchStores(
                        name: _searchController.text.isNotEmpty ? _searchController.text : null,
                        menuKeyword: storeProvider.currentSearchMenuKeyword,
                        category: _selectedCategory,
                        sortOption: newValue,
                      );
                    }
                  },
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStoreList(StoreProvider storeProvider) {
    // ... (이전 코드와 동일) ...
    if (storeProvider.isLoading && storeProvider.stores.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (storeProvider.errorMessage != null && storeProvider.stores.isEmpty) {
      return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        const Icon(Icons.error_outline, color: Colors.red, size: 60),
        Padding(padding: const EdgeInsets.all(16.0), child: Text('오류: ${storeProvider.errorMessage}', textAlign: TextAlign.center)),
        ElevatedButton(onPressed: _onRefresh, child: const Text('다시 시도'))
      ]));
    }
    if (storeProvider.stores.isEmpty) {
      return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Icon(Icons.storefront_outlined, color: Colors.grey[400], size: 80),
        const SizedBox(height: 16),
        const Text('조건에 맞는 가게가 없습니다.', style: TextStyle(fontSize: 16, color: Colors.grey)),
        const SizedBox(height: 8),
        ElevatedButton(onPressed: _onRefresh, child: const Text('초기화 및 새로고침'))
      ]));
    }

    return ListView.builder(
      controller: _scrollController,
      itemCount: storeProvider.stores.length + (storeProvider.isLastPage ? 0 : 1),
      itemBuilder: (context, index) {
        if (index == storeProvider.stores.length) {
          return storeProvider.isLoading
              ? const Padding(padding: EdgeInsets.all(16.0), child: Center(child: CircularProgressIndicator()))
              : const SizedBox.shrink();
        }
        final store = storeProvider.stores[index];
        return StoreCard(
          store: store,
          onTap: () {
            Navigator.of(context).pushNamed(StoreDetailScreen.routeName, arguments: store.id);
          },
        );
      },
    );
  }

  Widget _buildMapView(StoreProvider storeProvider) {
    if (!_mapControllerCompleter.isCompleted) {
      _mapControllerCompleter = Completer();
    }

    return NaverMap(
      options: NaverMapViewOptions(
        initialCameraPosition: _currentCameraPosition,
        locationButtonEnable: true,
      ),
      onMapReady: (controller) async {
        debugPrint("네이버맵 준비완료! (HomeScreen - _buildMapView)");
        if (!_mapControllerCompleter.isCompleted) {
          _mapControllerCompleter.complete(controller);
        }
        // 지도가 준비되면 Provider의 현재 가게 목록으로 마커 업데이트
        _updateMarkersOnMap(storeProvider.stores);
      },
      onMapTapped: (point, latLng) {
        debugPrint("지도 탭: ${latLng.latitude}, ${latLng.longitude}");
      },
      // onCameraChange 시그니처 수정
      onCameraChange: (NCameraUpdateReason reason, bool isAnimated) {
        debugPrint("카메라 변경 이유: $reason, 애니메이션: $isAnimated");
        // 현재 카메라 위치를 얻으려면 controller.getCameraPosition()을 사용해야 합니다.
        // 이 콜백은 NCameraPosition을 직접 제공하지 않습니다.
        // 필요하다면 onCameraIdle에서 최종 위치를 가져와 _currentCameraPosition을 업데이트합니다.
      },
      onCameraIdle: () async {
        if(_mapControllerCompleter.isCompleted) {
          final controller = await _mapControllerCompleter.future;
          final newPosition = await controller.getCameraPosition();
          setState(() {
            _currentCameraPosition = newPosition;
          });
          debugPrint("카메라 이동 멈춤: ${newPosition.target}");
          // TODO: 현재 보이는 지도 영역의 가게를 새로 로드하거나 필터링하는 로직 (선택적)
        }
      },
    );
  }
}
