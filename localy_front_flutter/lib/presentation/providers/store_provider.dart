// 파일 위치: lib/presentation/providers/store_provider.dart
import 'package:flutter/foundation.dart'; // foundation.dart 임포트 확인
import 'package:localy_front_flutter/data/models/store_models.dart';
import 'package:localy_front_flutter/data/services/store_api_service.dart';
import 'auth_provider.dart';

// 정렬 옵션 Enum (선택 사항, UI에서 사용 편의를 위해)
enum StoreSortOption {
  nameAsc, nameDesc,
  ratingAsc, ratingDesc,
  reviewCountAsc, reviewCountDesc,
  createdAtAsc, createdAtDesc,
  distance
}

class StoreProvider with ChangeNotifier { // "with ChangeNotifier" 추가/확인
  final StoreApiService _storeApiService;
  // _authProvider는 생성자에서 StoreApiService 초기화 시 사용됨
  final AuthProvider _authProvider;

  StoreProvider(this._authProvider)
      : _storeApiService = StoreApiService(apiClient: _authProvider.apiClient);

  List<Store> _stores = [];
  Store? _selectedStore;
  bool _isLoading = false;
  String? _errorMessage;

  String? _currentSearchName;
  String? _currentSearchCategory;
  String? _currentSearchMenuKeyword;
  StoreSortOption? _currentSortOption;
  int _currentPage = 0;
  final int _pageSize = 10;
  bool _isLastPage = false;

  List<Store> get stores => _stores;
  Store? get selectedStore => _selectedStore;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  bool get isLastPage => _isLastPage;
  int get currentPage => _currentPage;
  String? get currentSearchName => _currentSearchName;
  String? get currentSearchCategory => _currentSearchCategory;
  String? get currentSearchMenuKeyword => _currentSearchMenuKeyword;
  StoreSortOption? get currentSortOption => _currentSortOption;

  void _resetSearchState() {
    _stores = [];
    _currentPage = 0;
    _isLastPage = false;
    _errorMessage = null;
    // 검색 조건은 유지하거나, 필요시 초기화
  }

  Map<String, String?> _getSortParams(StoreSortOption? option) {
    if (option == null) return {'sortBy': null, 'sortDirection': null};
    switch (option) {
      case StoreSortOption.nameAsc:
        return {'sortBy': 'name', 'sortDirection': 'ASC'};
      case StoreSortOption.nameDesc:
        return {'sortBy': 'name', 'sortDirection': 'DESC'};
      case StoreSortOption.ratingAsc:
        return {'sortBy': 'averageRating', 'sortDirection': 'ASC'};
      case StoreSortOption.ratingDesc:
        return {'sortBy': 'averageRating', 'sortDirection': 'DESC'};
      case StoreSortOption.reviewCountAsc:
        return {'sortBy': 'reviewCount', 'sortDirection': 'ASC'};
      case StoreSortOption.reviewCountDesc:
        return {'sortBy': 'reviewCount', 'sortDirection': 'DESC'};
      case StoreSortOption.createdAtAsc:
        return {'sortBy': 'createdAt', 'sortDirection': 'ASC'};
      case StoreSortOption.createdAtDesc:
        return {'sortBy': 'createdAt', 'sortDirection': 'DESC'};
      case StoreSortOption.distance:
        return {'sortBy': 'distance', 'sortDirection': 'ASC'}; // 백엔드 지원 필요
      default:
        return {'sortBy': null, 'sortDirection': null};
    }
  }

  Future<void> fetchStores({
    String? name,
    String? category,
    String? menuKeyword,
    StoreSortOption? sortOption,
    bool loadMore = false,
  }) async {
    if (_isLoading && !loadMore) return;
    if (loadMore && _isLastPage) return;

    if (!loadMore) {
      _resetSearchState();
      _currentSearchName = name;
      _currentSearchCategory = category;
      _currentSearchMenuKeyword = menuKeyword;
      _currentSortOption = sortOption;
    } else {
      _currentPage++;
    }

    _isLoading = true;
    notifyListeners();

    try {
      final sortParams = _getSortParams(_currentSortOption);
      final List<Store> newStores = await _storeApiService.fetchStores(
        name: _currentSearchName,
        category: _currentSearchCategory,
        menuKeyword: _currentSearchMenuKeyword,
        sortBy: sortParams['sortBy'],
        sortDirection: sortParams['sortDirection'],
        page: _currentPage,
        size: _pageSize,
      );

      if (newStores.length < _pageSize) {
        _isLastPage = true;
      }

      if (loadMore) {
        _stores.addAll(newStores);
      } else {
        _stores = newStores;
      }
      _errorMessage = null;
    } catch (e) {
      _errorMessage = e.toString();
      if (loadMore) _currentPage--;
      debugPrint('--- StoreProvider: Error fetching stores: $_errorMessage ---');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> fetchStoreById(int storeId) async {
    _isLoading = true;
    _selectedStore = null;
    _errorMessage = null;
    notifyListeners();

    try {
      _selectedStore = await _storeApiService.fetchStoreById(storeId);
      _errorMessage = null;
    } catch (e) {
      _errorMessage = e.toString();
      debugPrint('--- StoreProvider: Error fetching store by ID: $_errorMessage ---');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshStores() async {
    await fetchStores(
      name: _currentSearchName,
      category: _currentSearchCategory,
      menuKeyword: _currentSearchMenuKeyword,
      sortOption: _currentSortOption,
      loadMore: false,
    );
  }

  void updateSearchName(String? name) {
    _currentSearchName = name;
  }
  void updateSearchCategory(String? category) {
    _currentSearchCategory = category;
  }
  void updateSearchMenuKeyword(String? menuKeyword) {
    _currentSearchMenuKeyword = menuKeyword;
  }
  void updateSortOption(StoreSortOption? sortOption) {
    _currentSortOption = sortOption;
  }
}
