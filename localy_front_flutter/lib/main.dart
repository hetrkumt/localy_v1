// 파일 위치: lib/main.dart
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart'; // 네이버 지도 패키지
import 'core/config/app_config.dart'; // 앱 설정 파일 import
import 'presentation/screens/auth/login_screen.dart'; // 로그인 화면 import
// import 'presentation/screens/home/home_screen.dart'; // (선택) 로그인 성공 후 보여줄 홈 화면
// import 'data/services/auth_api_service.dart'; // (선택) 앱 시작 시 토큰 확인용

void main() async {
  // Flutter 앱이 실행되기 전에 Flutter 엔진과 위젯 바인딩이 초기화되었는지 확인합니다.
  // 네이티브 플러그인(예: flutter_naver_map)을 사용하기 전에 필수입니다.
  WidgetsFlutterBinding.ensureInitialized();

  // 네이버 지도 SDK 초기화
  // 앱 실행 시 한 번만 수행하면 됩니다.
  await FlutterNaverMap().init(
    clientId: AppConfig.naverMapClientId, // AppConfig에서 클라이언트 ID 사용
    onAuthFailed: (ex) {
      // 인증 실패 시 콘솔에 에러를 출력합니다.
      // 실제 앱에서는 사용자에게 알림을 주거나 다른 적절한 처리를 할 수 있습니다.
      debugPrint("********* 네이버맵 인증오류 : $ex *********");
    },
  );

  // (선택 사항) 앱 시작 시 저장된 인증 토큰을 확인하여
  // 로그인 상태에 따라 초기 화면을 다르게 보여줄 수 있습니다.
  // final authService = AuthApiService(); // 실제로는 의존성 주입(DI) 사용 고려
  // final bool isLoggedIn = await authService.isUserLoggedIn();
  // final Widget initialScreen = isLoggedIn ? const HomeScreen() : const LoginScreen();

  runApp(const MyApp(
    // initialScreen: initialScreen, // (선택 사항) 위 로직 사용 시
  ));
}

class MyApp extends StatelessWidget {
  // final Widget initialScreen; // (선택 사항) 초기 화면을 외부에서 주입받을 경우

  const MyApp({super.key /*, required this.initialScreen */});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Localy', // 앱의 제목
      theme: ThemeData(
        primarySwatch: Colors.teal,
        scaffoldBackgroundColor: Colors.grey[50],
        appBarTheme: AppBarTheme(
          backgroundColor: Colors.teal[700],
          foregroundColor: Colors.white,
          elevation: 2.0,
          titleTextStyle: const TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.teal[600],
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide(color: Colors.grey[400]!),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide(color: Colors.teal[500]!, width: 2),
          ),
          labelStyle: TextStyle(color: Colors.grey[700]),
          floatingLabelStyle: TextStyle(color: Colors.teal[500]),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        ),
        cardTheme: CardTheme(
          elevation: 1.5,
          margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
        useMaterial3: true,
      ),
      debugShowCheckedModeBanner: false,
      home: const LoginScreen(), // 초기 화면을 로그인 화면으로 설정
      // routes: {
      //   '/': (context) => initialScreen,
      //   LoginScreen.routeName: (context) => const LoginScreen(),
      //   HomeScreen.routeName: (context) => const HomeScreen(),
      // },
    );
  }
}
