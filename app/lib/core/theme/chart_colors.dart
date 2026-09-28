import 'package:flutter/painting.dart';

import 'app_colors.dart';

/// 성적 막대(`CorrectCountChart`) **한 곳만** 쓰는 색. 웹 정본은
/// `frontend/src/shared/components/CorrectCountChart.tsx` 의 상수다.
///
/// **[AppColors] 에 올리지 마라**(CLAUDE.md 7-5). 버건디가 팔레트에 있으면
/// 버튼·배지로 번지고, 그러면 「빨강은 결석·위험 하나만」이 흐려진다. 웹이
/// tailwind 테마 대신 컴포넌트 상수로 둔 것과 같은 이유로 여기 따로 둔다 —
/// 앱은 화면에 색 리터럴을 둘 수 없어서(13) 파일만 테마 쪽이다.
class ChartColors {
  const ChartColors._();

  /// 내부지문. 버건디.
  static const internal = Color(0xFF7A2E3A);

  /// 외부지문·한 계열 막대. 로고·앱바와 같은 남색이라 화면에서 따로 놀지 않는다.
  static const external = AppColors.brand900;

  /// 주간 레포트에서 「지금 보고 있는 주」. 이 앱에서 주황은 「지금 여기」다.
  static const marked = AppColors.accent500;

  /// 눈금선(`#e2e8f0`)·눈금 글씨(`#94a3b8`)·주차 라벨(`#64748b`).
  static const grid = AppColors.slate200;
  static const tick = AppColors.slate400;
  static const label = AppColors.slate500;
}
