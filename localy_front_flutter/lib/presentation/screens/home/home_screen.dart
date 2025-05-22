// 파일 위치: lib/presentation/screens/home/home_screen.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
import 'package:localy_front_flutter/data/models/store_models.dart';
import 'package:localy_front_flutter/presentation/providers/auth_provider.dart';
import 'package:localy_front_flutter/presentation/providers/store_provider.dart';
import 'package:localy_front_flutter/presentation/screens/auth/login_screen.dart';
import 'package:localy_front_flutter/presentation/screens/cart/cart_screen.dart';
import 'package:localy_front_flutter/presentation/screens/order/order_list_screen.dart';
import 'package:localy_front_flutter/presentation/screens/store/store_detail_screen.dart';
import 'package:localy_front_flutter/presentation/screens/my_page/my_page_screen.dart';
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
  String? _selectedCategoryValue; // StoreCategory enum 값을 문자열로 저장
  StoreSortOption _selectedSortOption = StoreSortOption.createdAtDesc;
  final TextEditingController _searchController = TextEditingController();
  Timer? _debounce;

  NCameraPosition _currentCameraPosition = const NCameraPosition(
    target: NLatLng(37.5666102, 126.9783881), // 서울 시청
    zoom: 12,
  );
  int _currentBottomNavIndex = 0;

  // Helper 메서드를 _HomeScreenState 클래스의 멤버로 이동
  String _getSortOptionText(StoreSortOption option) {
    switch (option) {
      case StoreSortOption.createdAtDesc: return '최신순';
      case StoreSortOption.ratingDesc: return '평점 높은순';
      case StoreSortOption.reviewCountDesc: return '리뷰 많은순';
      case StoreSortOption.nameAsc: return '이름 오름차순';
      case StoreSortOption.createdAtAsc: return '오래된순';
      case StoreSortOption.nameDesc: return '이름 내림차순';
      case StoreSortOption.ratingAsc: return '평점 낮은순';
      case StoreSortOption.reviewCountAsc: return '리뷰 적은순';
      default: return option.name; // 기본값으로 enum 이름 사용
    }
  }
  String _storeCategoryToKo(StoreCategory category) {
    switch (category) {
      case StoreCategory.FRUITS_VEGETABLES: return "과일/채소";
      case StoreCategory.MEAT_BUTCHER: return "정육점";
      case StoreCategory.FISH_SEAFOOD: return "생선/해산물";
      case StoreCategory.RICE_GRAINS: return "쌀/잡곡";
      case StoreCategory.SIDE_DISHES: return "반찬";
      case StoreCategory.DAIRY_PRODUCTS: return "유제품";
      case StoreCategory.BREAD_BAKERY: return "빵/베이커리";
      case StoreCategory.NUTS_DRIED_FRUITS: return "견과/건과";
      case StoreCategory.KOREAN_FOOD: return "한식";
      case StoreCategory.SNACKS_STREET_FOOD: return "분식/길거리음식";
      case StoreCategory.CHINESE_FOOD: return "중식";
      case StoreCategory.JAPANESE_FOOD: return "일식";
      case StoreCategory.WESTERN_FOOD: return "양식";
      case StoreCategory.CAFE_DESSERT: return "카페/디저트";
      case StoreCategory.CHICKEN_BURGER: return "치킨/버거";
      case StoreCategory.HOUSEHOLD_GOODS: return "생활용품";
      case StoreCategory.UNKNOWN: return "기타";
      default: return ''; // 한글 이름이 없는 경우 빈 문자열 반환
    }
  }

  String _getCategoryDisplayText(String? categoryValue) {
    if (categoryValue == null) return '전체 카테고리';
    try {
      StoreCategory enumCat = StoreCategory.values.firstWhere((cat) => storeCategoryToString(cat) == categoryValue);
      return _storeCategoryToKo(enumCat);
    } catch (e) {
      return '카테고리'; // 일치하는 enum 못 찾으면 기본값
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadInitialStores();
    });
    _scrollController.addListener(() {
      final storeProvider = context.read<StoreProvider>();
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 300 &&
          !storeProvider.isLoading &&
          !storeProvider.isLastPage) {
        storeProvider.fetchStores(
          name: storeProvider.currentSearchName,
          category: storeProvider.currentSearchCategory,
          menuKeyword: storeProvider.currentSearchMenuKeyword,
          sortOption: storeProvider.currentSortOption,
          loadMore: true,
        );
      }
    });
  }

  Future<void> _loadInitialStores() async {
    final storeProvider = Provider.of<StoreProvider>(context, listen: false);
    storeProvider.updateSearchName(_searchController.text.isNotEmpty ? _searchController.text : null);
    storeProvider.updateSearchCategory(_selectedCategoryValue);
    storeProvider.updateSearchMenuKeyword(_searchController.text.isNotEmpty ? _searchController.text : null);
    storeProvider.updateSortOption(_selectedSortOption);
    await storeProvider.fetchStores(
        name: storeProvider.currentSearchName,
        category: storeProvider.currentSearchCategory,
        menuKeyword: storeProvider.currentSearchMenuKeyword,
        sortOption: storeProvider.currentSortOption);
    if (!_isListView && mounted && _mapControllerCompleter.isCompleted) {
      _updateMarkersOnMap(storeProvider.stores);
    }
  }

  Future<void> _onRefresh() async {
    final storeProvider = Provider.of<StoreProvider>(context, listen: false);
    await storeProvider.refreshStores();
    if (!_isListView && mounted && _mapControllerCompleter.isCompleted) {
      _updateMarkersOnMap(storeProvider.stores);
    }
  }

  void _onSearchChanged(String query) {
    if (_debounce?.isActive ?? false) _debounce!.cancel();
    _debounce = Timer(const Duration(milliseconds: 700), () {
      final storeProvider = Provider.of<StoreProvider>(context, listen: false);
      storeProvider.updateSearchName(query.isNotEmpty ? query : null);
      storeProvider.updateSearchMenuKeyword(query.isNotEmpty ? query : null);
      storeProvider.fetchStores(loadMore: false);
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
    }
  }

  void _handleMarkerTap(NMarker tappedMarker, List<Store> currentStores) {
    try {
      final store = currentStores.firstWhere(
            (s) => s.id.toString() == tappedMarker.info.id,
      );
      _displayStoreInfoBottomSheet(store);
    } catch (e) {
      debugPrint("마커 탭 오류: $e");
    }
  }

  void _displayStoreInfoBottomSheet(Store store) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
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
                boxShadow: [ BoxShadow(color: Colors.black.withOpacity(0.15), spreadRadius: 0, blurRadius: 10, offset: const Offset(0, -2)) ],
              ),
              child: ListView(
                controller: scrollController,
                children: <Widget>[
                  Center(child: Container(width: 40, height: 5, margin: const EdgeInsets.only(bottom: 12), decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(10)))),
                  Text(store.name, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Row(children: [
                    Icon(Icons.star_rounded, color: Colors.amber[600], size: 18), const SizedBox(width: 4),
                    Text(store.averageRating?.toStringAsFixed(1) ?? 'N/A', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                    const SizedBox(width: 6),
                    Text('(${store.reviewCount ?? 0} 리뷰)', style: TextStyle(fontSize: 13, color: Colors.grey[600])),
                  ]),
                  const SizedBox(height: 12),
                  if (store.address != null) ListTile(leading: Icon(Icons.location_on_outlined, color: Theme.of(context).primaryColor), title: Text(store.address!), dense: true, contentPadding: EdgeInsets.zero),
                  if (store.phone != null) ListTile(leading: Icon(Icons.phone_outlined, color: Theme.of(context).primaryColor), title: Text(store.phone!), dense: true, contentPadding: EdgeInsets.zero),
                  if (store.openingHours != null) ListTile(leading: Icon(Icons.access_time_outlined, color: Theme.of(context).primaryColor), title: Text(store.openingHours!), dense: true, contentPadding: EdgeInsets.zero),
                  ListTile(leading: Icon(Icons.category_outlined, color: Theme.of(context).primaryColor), title: Text('카테고리: ${_storeCategoryToKo(store.category)}'), dense: true, contentPadding: EdgeInsets.zero),
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

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context, listen: false);
    final storeProvider = Provider.of<StoreProvider>(context);

    if (!_isListView && _mapControllerCompleter.isCompleted) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
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
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted && _mapControllerCompleter.isCompleted) {
                      _updateMarkersOnMap(storeProvider.stores);
                    } else if (mounted && !_mapControllerCompleter.isCompleted) {
                      debugPrint("지도 보기 전환: 지도 컨트롤러 아직 준비 안됨");
                    }
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
        currentIndex: _currentBottomNavIndex,
        onTap: (index) {
          if (index == _currentBottomNavIndex && index == 0) {
            if (_isListView) {
              if (_scrollController.hasClients) {
                _scrollController.animateTo(0, duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
              }
            }
            _onRefresh();
            return;
          }
          setState(() { _currentBottomNavIndex = index; });

          if (index == 0) { /* 홈 화면 (현재) */ }
          else if (index == 1) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("검색 화면 (구현 예정)"))); }
          else if (index == 2) { Navigator.of(context).pushNamed(CartScreen.routeName); }
          else if (index == 3) { Navigator.of(context).pushNamed(OrderListScreen.routeName); }
          else if (index == 4) { Navigator.of(context).pushNamed(MyPageScreen.routeName); }
        },
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_outlined), activeIcon: Icon(Icons.home), label: '홈'),
          BottomNavigationBarItem(icon: Icon(Icons.search_outlined), activeIcon: Icon(Icons.search), label: '검색'),
          BottomNavigationBarItem(icon: Icon(Icons.shopping_cart_outlined), activeIcon: Icon(Icons.shopping_cart), label: '장바구니'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt_long_outlined), activeIcon: Icon(Icons.receipt_long), label: '주문내역'),
          BottomNavigationBarItem(icon: Icon(Icons.person_outline_rounded), activeIcon: Icon(Icons.person_rounded), label: '마이페이지'),
        ],
        selectedItemColor: Theme.of(context).primaryColor,
        unselectedItemColor: Colors.grey[600],
        showUnselectedLabels: true,
        selectedFontSize: 12,
        unselectedFontSize: 12,
      ),
    );
  }

  Widget _buildSearchAndFilterBar(StoreProvider storeProvider) {
    final ThemeData theme = Theme.of(context);
    InputDecoration dropdownDecoration(String label, IconData iconData) {
      return InputDecoration(
        prefixIcon: Padding(
          padding: const EdgeInsets.only(left: 12.0, right: 8.0),
          child: Icon(iconData, size: 20, color: Colors.grey[600]),
        ),
        hintText: label,
        hintStyle: TextStyle(color: Colors.grey[600], fontSize: 14, fontWeight: FontWeight.w500),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10.0), borderSide: BorderSide(color: Colors.grey[300]!)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10.0), borderSide: BorderSide(color: Colors.grey[300]!)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10.0), borderSide: BorderSide(color: theme.primaryColor, width: 1.5)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 0, vertical: 12.0),
        isDense: true,
        fillColor: Colors.white,
        filled: true,
      );
    }

    return Container(
      padding: const EdgeInsets.fromLTRB(16.0, 12.0, 16.0, 16.0),
      color: Colors.grey[50],
      child: Column(
        children: [
          TextField(
            controller: _searchController,
            decoration: InputDecoration(
              hintText: '가게 또는 메뉴 이름으로 검색...',
              hintStyle: TextStyle(fontSize: 15, color: Colors.grey[500]),
              prefixIcon: Icon(Icons.search_rounded, color: Colors.grey[600], size: 22),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(25.0), borderSide: BorderSide.none),
              filled: true,
              fillColor: Colors.white,
              contentPadding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
              suffixIcon: _searchController.text.isNotEmpty
                  ? IconButton(
                  icon: Icon(Icons.clear_rounded, color: Colors.grey[600], size: 20),
                  onPressed: (){
                    _searchController.clear();
                    _onSearchChanged('');
                  })
                  : null,
            ),
            onChanged: _onSearchChanged,
            textInputAction: TextInputAction.search,
            onSubmitted: _onSearchChanged,
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  decoration: dropdownDecoration('카테고리', Icons.filter_list_rounded),
                  value: _selectedCategoryValue,
                  isExpanded: true,
                  icon: Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: Icon(Icons.keyboard_arrow_down_rounded, color: Colors.grey[700], size: 24),
                  ),
                  items: [
                    const DropdownMenuItem<String>(value: null, child: Text('전체 카테고리', style: TextStyle(color: Colors.grey, fontSize: 14))),
                    ...StoreCategory.values.where((cat) => cat != StoreCategory.UNKNOWN).map((StoreCategory category) {
                      return DropdownMenuItem<String>(
                        value: storeCategoryToString(category),
                        child: Text(_storeCategoryToKo(category), style: const TextStyle(fontSize: 14)),
                      );
                    }).toList(),
                  ],
                  onChanged: (String? newValue) {
                    setState(() { _selectedCategoryValue = newValue; });
                    storeProvider.applyCategoryFilter(newValue);
                  },
                  selectedItemBuilder: (BuildContext context) {
                    return [ // DropdownButtonFormField는 첫 번째 위젯만 사용
                      Padding(
                        padding: const EdgeInsets.only(left:0.0),
                        child: Text(
                          _getCategoryDisplayText(_selectedCategoryValue),
                          style: TextStyle(fontSize: 14, color: _selectedCategoryValue == null ? Colors.grey[700] : Colors.black87, fontWeight: FontWeight.w500),
                          overflow: TextOverflow.ellipsis,
                        ),
                      )
                    ];
                  },
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: DropdownButtonFormField<StoreSortOption>(
                  decoration: dropdownDecoration('정렬', Icons.sort_rounded),
                  value: _selectedSortOption,
                  isExpanded: true,
                  icon: Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: Icon(Icons.keyboard_arrow_down_rounded, color: Colors.grey[700], size: 24),
                  ),
                  items: StoreSortOption.values.map((StoreSortOption option) {
                    return DropdownMenuItem<StoreSortOption>(
                        value: option,
                        child: Text(_getSortOptionText(option), style: const TextStyle(fontSize: 14))
                    );
                  }).toList(),
                  onChanged: (StoreSortOption? newValue) {
                    if (newValue != null) {
                      setState(() { _selectedSortOption = newValue; });
                      storeProvider.applySortOption(newValue);
                    }
                  },
                  selectedItemBuilder: (BuildContext context) {
                    return [
                      Padding(
                        padding: const EdgeInsets.only(left:0.0),
                        child: Text(
                          _getSortOptionText(_selectedSortOption),
                          style: const TextStyle(fontSize: 14, color: Colors.black87, fontWeight: FontWeight.w500),
                          overflow: TextOverflow.ellipsis,
                        ),
                      )
                    ];
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
    if (storeProvider.isLoading && storeProvider.stores.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (storeProvider.errorMessage != null && storeProvider.stores.isEmpty) {
      return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 60),
        Padding(padding: const EdgeInsets.all(16.0), child: Text('가게 목록을 불러오는 중 오류가 발생했습니다.\n${storeProvider.errorMessage}', textAlign: TextAlign.center)),
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
      padding: const EdgeInsets.only(top: 4, bottom: 8),
      itemCount: storeProvider.stores.length + (storeProvider.isLastPage || storeProvider.stores.isEmpty ? 0 : 1),
      itemBuilder: (context, index) {
        if (index == storeProvider.stores.length) {
          return storeProvider.isLoading && !storeProvider.isLastPage
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
      _mapControllerCompleter = Completer<NaverMapController>();
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
        _updateMarkersOnMap(storeProvider.stores);
      },
      onMapTapped: (point, latLng) {
        debugPrint("지도 탭: ${latLng.latitude}, ${latLng.longitude}");
      },
      onCameraChange: (NCameraUpdateReason? reason, bool? isAnimated) {
        debugPrint("카메라 변경 이유: $reason, 애니메이션: $isAnimated");
      },
      onCameraIdle: () async {
        if(_mapControllerCompleter.isCompleted) {
          final controller = await _mapControllerCompleter.future;
          final newPosition = await controller.getCameraPosition();
          if (mounted) {
            setState(() { _currentCameraPosition = newPosition; });
          }
          debugPrint("카메라 이동 멈춤: ${newPosition.target}");
        }
      },
    );
  }
}
