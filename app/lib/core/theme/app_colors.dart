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
  static const brand100 = Color(0xFFDAE6F7);

  /// 남색 띠 위의 보조 글자(역할 칩·지면의 라벨). 흰색이면 본문과 같은 무게가 된다.
  static const brand200 = Color(0xFFB8CEEF);
  static const brand300 = Color(0xFF8AAFE2);
  static const brand600 = Color(0xFF1E5AA8);
  static const brand700 = Color(0xFF1A4A8A);
  static const brand900 = Color(0xFF1B2A44);

  /// 틴트 면 위의 본문 잉크. brand900 보다 한 단계 어두워서, 옅은 남색 면
  /// 위에 얹었을 때 제목이 배경과 붙지 않는다.
  static const brand950 = Color(0xFF111B2C);

  /// 지면의 hot 칸 배경과 텍스트. accent500 을 그대로 쓰면 남색 위에서 너무 튄다.
  static const accent100 = Color(0xFFFADFD3);
  static const accent200 = Color(0xFFF4BCA6);
  static const accent300 = Color(0xFFEC9271);
  static const accent400 = Color(0xFFE36F49);
  static const accent500 = Color(0xFFD9542B);

  /// 미완료 숙제 줄의 배지 글자. **주황 면 위의 배지는 뒤집는다** — 흰 바탕에
  /// 이 색 글씨다. 옅은 배지는 accent50 블록에 묻힌다.
  static const accent600 = Color(0xFFBF4522);

  /// 같은 줄의 마감 시각. 제목(brand900)보다 약하고 배지보다 어둡다.
  static const accent700 = Color(0xFF98371C);

  /// 「안 낸 숙제」 구획의 틴트 배경. tailwind accent.50.
  static const accent50 = Color(0xFFFDF2ED);

  /// 페이지 배경. 흰 카드가 떠 보이려면 배경이 흰색이 아니어야 한다.
  static const paper = Color(0xFFEDF1F7);

  /// 상태·회색 계열. **웹의 tailwind 기본 팔레트를 그대로 쓴다.**
  ///
  /// 값을 여기서 발명하지 마라 — 정본은 tailwind 이고 화면은 이 이름만 쓴다.
  /// 화면에 `Color(0xFFB91C1C)`처럼 박으면 같은 빨강이 두 곳에 생기고,
  /// 「빨강은 결석·위험 하나만」이 흐려진다.
  ///
  /// **emerald·sky 를 넣지 마라.** 인증 화면이 쓰지 않는다 —
  /// 필요해지는 화면에서 같은 방식으로 추가한다.
  static const slate50 = Color(0xFFF8FAFC);
  static const slate100 = Color(0xFFF1F5F9);
  static const slate200 = Color(0xFFE2E8F0);
  static const slate300 = Color(0xFFCBD5E1);
  static const slate400 = Color(0xFF94A3B8);
  static const slate500 = Color(0xFF64748B);
  static const slate600 = Color(0xFF475569);
  static const slate700 = Color(0xFF334155);
  static const slate900 = Color(0xFF0F172A);

  static const red50 = Color(0xFFFEF2F2);
  static const red200 = Color(0xFFFECACA);
  static const red700 = Color(0xFFB91C1C);

  static const amber50 = Color(0xFFFFFBEB);
  static const amber800 = Color(0xFF92400E);
  static const amber900 = Color(0xFF78350F);
}
