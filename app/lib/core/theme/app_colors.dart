import 'package:flutter/material.dart';

/// 웹의 `frontend/tailwind.config.js`가 정본이다. 값을 여기서 발명하지 마라.
///
/// brand 한 줄기가 두 가지 일을 한다 — 900은 앱바 띠와 본문 잉크, 600은 링크·버튼.
/// Tailwind 기본 blue를 쓰지 않는다. 그 형광 파랑은 IT 서비스로 읽힌다.
///
/// **accent(주황)는 「아직 안 한 것」 또는 「지금 여기」 하나만 뜻한다.**
/// 버튼·링크로 넓히지 마라 — amber(경고)와 red(결석·위험) 사이에 끼어 있어서
/// 눌러서 뭔가 되는 곳까지 주황이면 그 두 색이 뜻하던 게 흐려진다.
/// A단계에는 accent를 쓰는 화면이 없다. 상수만 먼저 둔다.
class AppColors {
  const AppColors._();

  static const brand50 = Color(0xFFEFF4FC);
  static const brand600 = Color(0xFF1E5AA8);
  static const brand900 = Color(0xFF1B2A44);
  static const accent500 = Color(0xFFD9542B);

  /// 페이지 배경. 흰 카드가 떠 보이려면 배경이 흰색이 아니어야 한다.
  static const paper = Color(0xFFEDF1F7);
}
