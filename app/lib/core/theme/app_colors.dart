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

  /// 출석 캘린더의 토요일 머리글(웹 `text-brand-400`).
  static const brand400 = Color(0xFF5688D2);
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
  /// 쓰는 화면이 생길 때 그 태스크가 추가한다 — emerald 는 B2 숙제 배지가,
  /// sky·teal 은 B3 출석 캘린더가 그렇게 넣었다. 미리 올리지 마라.
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

  /// 여기부터 red 의 100·800·600 은 **출석 캘린더의 「결석」** 하나다(웹
  /// `attendance/types.ts` 의 `DAY_STATUS_STYLE`·요약 칸). 400 은 일요일 머리글.
  static const red100 = Color(0xFFFEE2E2);
  static const red200 = Color(0xFFFECACA);
  static const red400 = Color(0xFFF87171);
  static const red600 = Color(0xFFDC2626);
  static const red700 = Color(0xFFB91C1C);
  static const red800 = Color(0xFF991B1B);

  /// 배지 `ok`(웹 `Badge.tsx` 의 `bg-emerald-50 text-emerald-700 ring-emerald-200`).
  /// ⭕·「제출 완료」 한 가지 뜻이다. B2 숙제 화면이 처음 쓴다 —
  /// 쓰는 화면 없이 초록 계열을 더 늘리지 마라.
  static const emerald50 = Color(0xFFECFDF5);

  /// 100·800 은 캘린더 칩 「출석」, 600 은 요약 칸의 출석 숫자다(B3).
  static const emerald100 = Color(0xFFD1FAE5);
  static const emerald200 = Color(0xFFA7F3D0);
  static const emerald600 = Color(0xFF059669);
  static const emerald700 = Color(0xFF047857);
  static const emerald800 = Color(0xFF065F46);

  /// 캘린더 칩 「병결」과 요약 칸의 「병·공결」 숫자(B3). 이 둘 말고 쓰지 마라.
  static const sky100 = Color(0xFFE0F2FE);
  static const sky200 = Color(0xFFBAE6FD);
  static const sky600 = Color(0xFF0284C7);
  static const sky800 = Color(0xFF075985);

  /// 캘린더 칩 「대체 등원」(B3). 출석으로 세지만 날은 구분해 보여준다(5-1).
  static const teal100 = Color(0xFFCCFBF1);
  static const teal200 = Color(0xFF99F6E4);
  static const teal800 = Color(0xFF115E59);

  /// 캘린더 칩 「온라인」(2026-09-29). 웹 `bg-violet-100 text-violet-800 ring-violet-200`.
  /// 하늘색은 병결이 쓴다. 칩 말고 다른 자리에 쓰지 마라.
  static const violet100 = Color(0xFFEDE9FE);
  static const violet200 = Color(0xFFDDD6FE);
  static const violet800 = Color(0xFF5B21B6);

  /// 성적 배지 「재시험 통과」 **하나만**이다(웹 `ScoreValue.tsx` 의
  /// `bg-blue-100 text-blue-700`). 위 brand 주석의 「tailwind blue 를 쓰지 않는다」는
  /// 브랜드·링크 이야기고, 이것은 웹이 그 배지에 실제로 쓰는 값이다. 넓히지 마라.
  static const blue100 = Color(0xFFDBEAFE);
  static const blue700 = Color(0xFF1D4ED8);

  static const amber50 = Color(0xFFFFFBEB);

  /// 캘린더 칩 「지각」 바탕과 요약 칸의 지각 숫자(B3).
  static const amber100 = Color(0xFFFEF3C7);
  static const amber600 = Color(0xFFD97706);

  /// 수강 후기의 **채운 별 하나뿐이다**(웹 `StarRating.tsx` 의 `text-amber-400`, B4).
  /// 배지·버튼으로 넓히지 마라 — 경고(amber700)와 헷갈린다.
  static const amber400 = Color(0xFFFBBF24);

  /// 배지 `warn` 의 테두리·글자(웹 `Badge.tsx` 의 `ring-amber-200 text-amber-700`).
  /// 🔺·「다시 제출 필요」·「늦게 냄」이 이 색이다.
  static const amber200 = Color(0xFFFDE68A);
  static const amber700 = Color(0xFFB45309);
  static const amber800 = Color(0xFF92400E);
  static const amber900 = Color(0xFF78350F);
}
