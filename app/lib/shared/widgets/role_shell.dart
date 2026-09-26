import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/routes.dart';
import '../../core/theme/app_colors.dart';
import 'bottom_tab_bar.dart';

/// 학생·학부모가 공유하는 셸. **하나로 두는 것이 의도다** — 웹이
/// `StudentLayout`·`ParentLayout` 둘로 나눈 것은 React 라우팅 구조 때문이지
/// 설계 의도가 아니다. 둘로 두면 탭 바·앱바·여백이 조용히 어긋난다.
///
/// 이동은 전부 아래에 있다 — 앱바는 제목 줄만 남았다.
///
/// **앱바(`AppBarBand`)는 셸이 그리지 않는다. 각 화면이 자기 스크롤 맨 위에
/// 그린다** — 웹 `StudentLayout` 의 `<AppBar>` 가 페이지와 함께 스크롤되는
/// 것과 같다. 셸에 고정해 두면 홈의 지면(`-40`)이 앱바가 아니라 목록
/// 뷰포트 위로 끌어올려져 잘린다(날짜 줄이 통째로 안 보였다). 스텁은
/// `StubPage` 가 그린다.
///
/// **`extendBody: true` 를 빼지 마라.** 웹의 하단 바는 `fixed … backdrop-blur`
/// 라 콘텐츠가 그 밑을 흐르고, 블러는 밑에 뭔가 있어야 보인다. 빼면 본문이
/// 바 위에서 끝나 블러가 영영 안 보이고, 화면들이 끝에 두는 여백
/// (`MediaQuery.paddingOf(context).bottom` = 바 높이 + 홈 인디케이터)이 0 이
/// 된다.
class RoleShell extends StatelessWidget {
  const RoleShell({
    super.key,
    required this.role,
    required this.tabs,
    required this.navigationShell,
  });

  /// 「학생」 · 「학부모」
  final String role;
  final List<BottomTab> tabs;
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.paper,
      extendBody: true,
      body: navigationShell,
      bottomNavigationBar: BottomTabBar(
        tabs: tabs,
        currentIndex: navigationShell.currentIndex,
        // 같은 탭을 다시 누르면 그 갈래의 첫 화면으로 돌아간다.
        onTap: (i) => navigationShell.goBranch(
          i,
          initialLocation: i == navigationShell.currentIndex,
        ),
      ),
    );
  }
}
