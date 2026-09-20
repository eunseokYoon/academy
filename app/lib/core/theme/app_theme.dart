import 'package:flutter/material.dart';

import 'app_colors.dart';

/// 웹의 `rounded-lg` · `rounded-xl` · `rounded-2xl`. 그 셋만 쓴다.
///
/// 화면에서 `BorderRadius.circular(14)` 처럼 숫자를 발명하지 마라 —
/// 웹에 없는 값이면 웹과 달라진다.
class AppRadii {
  const AppRadii._();

  static const double lg = 8;
  static const double xl = 12;
  static const double xxl = 16;
}

/// 웹 `tailwind.config.js` 의 `boxShadow.card` 를 그대로 옮긴 것.
///
/// **Material `elevation` 을 쓰지 마라.** 그것은 한 겹 회색이고, 웹 주석이
/// 이유를 적어 뒀다 — 「회색 그림자는 이 배경과 따로 논다」. 남색이 섞여 있어야
/// paper 배경 위에서 카드가 떠 보인다.
class AppShadows {
  const AppShadows._();

  /// `0 1px 2px rgba(20,41,77,0.05)` + `0 6px 16px -6px rgba(20,41,77,0.10)`
  static const List<BoxShadow> card = [
    BoxShadow(color: Color(0x0D14294D), offset: Offset(0, 1), blurRadius: 2),
    BoxShadow(
      color: Color(0x1A14294D),
      offset: Offset(0, 6),
      blurRadius: 16,
      spreadRadius: -6,
    ),
  ];

  /// 웹 `boxShadow.sm` 을 그대로 옮긴 것 — tailwind 기본 `shadow-sm` 을
  /// 덮어쓴 값이라 `card` 와 레이어 수치가 다르다.
  /// `0 1px 2px rgba(20,41,77,0.06)` + `0 4px 10px -6px rgba(20,41,77,0.10)`
  static const List<BoxShadow> sm = [
    BoxShadow(color: Color(0x0F14294D), offset: Offset(0, 1), blurRadius: 2),
    BoxShadow(
      color: Color(0x1A14294D),
      offset: Offset(0, 4),
      blurRadius: 10,
      spreadRadius: -6,
    ),
  ];
}

/// 숫자 폭 고정(웹의 `.tnum`). D-day·점수·개수가 갱신될 때 옆 글자가 밀리지 않는다.
/// 한 곳에 둔다 — 위젯마다 따로 적으면 어느 숫자는 밀리고 어느 숫자는 안 밀린다.
const List<FontFeature> kTabularFigures = [FontFeature.tabularFigures()];

/// 화면은 모바일 먼저다. 360px에서 안 깨지면 된다.
class AppTheme {
  const AppTheme._();

  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.brand600,
      brightness: Brightness.light,
    ).copyWith(primary: AppColors.brand600, surface: Colors.white);

    return ThemeData(
      useMaterial3: true,
      // 웹과 같은 서체다(frontend/index.html 이 Pretendard Variable 을 받는다).
      // Variable 폰트라 fontWeight 가 wght 축으로 전달된다 — 굵기별 파일이 없다.
      fontFamily: 'Pretendard',
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.paper,
      appBarTheme: const AppBarTheme(
        // 앱바가 곧 로고가 놓이는 자리라 brand900이어야 한다
        backgroundColor: AppColors.brand900,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.brand600,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.brand50),
        ),
      ),
    );
  }
}

/// 홈 지면(`.field`)의 남색. 웹 `index.css` 의
/// `linear-gradient(172deg, #1b2a44 0%, #1d2e4b 42%, #16233a 100%)` 이다.
///
/// **맨 위는 반드시 brand900(#1B2A44) 이다.** 앱바가 그 단색이라 다른 값으로
/// 시작하면 지면이 앱바를 파고든 자리에 가로줄이 그어진다 — 두 남색이 한
/// 덩어리로 읽혀야 하는데 상자 두 개로 보인다.
/// **아래로만 어두워진다.** 밝히는 방향으로 뒤집지 마라.
///
/// 172deg 를 Alignment 로 옮긴 값이다(에뮬레이터에서 렌더 확인).
const LinearGradient fieldGradient = LinearGradient(
  begin: Alignment(-0.139, -0.990),
  end: Alignment(0.139, 0.990),
  colors: [Color(0xFF1B2A44), Color(0xFF1D2E4B), Color(0xFF16233A)],
  stops: [0.0, 0.42, 1.0],
);
