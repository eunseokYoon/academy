import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import 'app_bar_band.dart';

/// 홈(S-1·P-1)의 겹침 구조가 끝에서 한 번 더 끌어올리는 양. 지면 `-40` 뒤에
/// 레일 카드와 나머지 구획이 누적 `-80` 으로 그려진다.
///
/// `Transform.translate` 는 그리기만 옮기고 레이아웃은 안 옮기므로, 마지막
/// 카드가 **그려진** 자리는 레이아웃보다 이만큼 위다 — 목록 끝의 여백은 이
/// 값만큼 이미 있는 셈이다.
const double kHomeLift = 80;

/// 스크롤을 끝까지 내렸을 때 마지막 카드와 하단 탭 바 윗변 사이의 틈.
/// 웹 `main` 의 `pb-24`(96) 에서 탭 바(56 + 테두리 1)를 뺀 값(≈39)이다.
const double kHomeTailGap = 40;

/// 홈 목록 맨 끝에 두는 여백.
///
/// 셸이 `extendBody` 라 목록이 탭 바 밑까지 내려오고, `Scaffold` 가 바 높이
/// (홈 인디케이터 포함)를 `MediaQuery` 의 아래 여백으로 넘겨준다. 거기에 틈을
/// 더하고 이미 생긴 [kHomeLift] 를 뺀다. **고정값으로 되돌리지 마라** — 예전의
/// `176` 은 바 높이를 두 번 세고 `-80` 을 더해 마지막 카드 밑이 256px 비었다.
double homeTailSpace(BuildContext context) => math.max(
  0,
  MediaQuery.paddingOf(context).bottom + kHomeTailGap - kHomeLift,
);

/// 홈에 그릴 데이터가 아직 없을 때(첫 로딩·첫 실패)의 틀. 셸이 앱바를 그리지
/// 않으므로 여기서도 앱바를 먼저 그린다 — 안 그러면 로딩 동안 역할 칩이
/// 사라졌다가 나타난다. 본문은 탭 바 밑으로 들어가지 않게 띄운다.
class HomeStatusFrame extends StatelessWidget {
  const HomeStatusFrame({super.key, required this.role, required this.child});

  /// 「학생」 · 「학부모」
  final String role;
  final Widget child;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      AppBarBand(role: role),
      Expanded(child: SafeArea(top: false, child: child)),
    ],
  );
}
