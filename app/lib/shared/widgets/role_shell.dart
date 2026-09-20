import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/routes.dart';
import '../../core/theme/app_colors.dart';
import 'app_bar_band.dart';
import 'bottom_tab_bar.dart';

/// 학생·학부모가 공유하는 셸. **하나로 두는 것이 의도다** — 웹이
/// `StudentLayout`·`ParentLayout` 둘로 나눈 것은 React 라우팅 구조 때문이지
/// 설계 의도가 아니다. 둘로 두면 탭 바·앱바·여백이 조용히 어긋난다.
///
/// 이동은 전부 아래에 있다 — 앱바는 제목 줄만 남았다.
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
      body: Column(
        children: [
          AppBarBand(role: role),
          Expanded(child: navigationShell),
        ],
      ),
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
