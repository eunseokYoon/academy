import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/router/routes.dart';
import 'package:academy_app/core/router/auth_redirect.dart';
import 'package:academy_app/shared/widgets/app_bar_band.dart';
import 'package:academy_app/shared/widgets/bottom_tab_bar.dart';
import 'package:academy_app/shared/widgets/role_shell.dart';

/// 탭마다 식별 가능한 글자만 그리는 최소 라우터.
GoRouter buildTestShell() {
  return GoRouter(
    initialLocation: AppRoutes.student,
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (_, _, shell) =>
            RoleShell(role: '학생', tabs: kStudentTabs, navigationShell: shell),
        branches: [
          for (final t in kStudentTabs)
            StatefulShellBranch(
              routes: [
                GoRoute(
                  path: t.route,
                  builder: (_, _) => Center(child: Text('화면:${t.label}')),
                ),
              ],
            ),
        ],
      ),
    ],
  );
}

void main() {
  Future<void> pump(WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp.router(routerConfig: buildTestShell()));
    await tester.pumpAndSettle();
  }

  testWidgets('하단 탭 바는 셸이 그리고, 앱바는 셸이 그리지 않는다', (tester) async {
    // 앱바는 화면이 자기 스크롤 맨 위에 그린다(웹 StudentLayout 처럼 같이
    // 스크롤된다). 셸이 그리면 홈 지면의 -40 이 잘린다 —
    // shell_geometry_test 의 (a) 가 그 기하를 잰다.
    await pump(tester);
    expect(find.byType(BottomTabBar), findsOneWidget);
    expect(find.byType(AppBarBand), findsNothing);
  });

  testWidgets('처음에는 홈 갈래가 보인다', (tester) async {
    await pump(tester);
    expect(find.text('화면:홈'), findsOneWidget);
  });

  testWidgets('탭을 누르면 그 갈래로 옮겨간다', (tester) async {
    await pump(tester);
    await tester.tap(find.text('숙제'));
    await tester.pumpAndSettle();
    expect(find.text('화면:숙제'), findsOneWidget);
  });

  testWidgets('탭을 옮겼다 돌아와도 갈래가 유지된다', (tester) async {
    // indexedStack 이라 갈래가 살아 있어야 한다. 매번 새로 만들면
    // 홈에 돌아올 때마다 스피너가 번쩍이고 스크롤이 맨 위로 튄다.
    await pump(tester);
    await tester.tap(find.text('수업'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('홈'));
    await tester.pumpAndSettle();
    expect(find.text('화면:홈'), findsOneWidget);
    // IndexedStack 은 보이지 않는 갈래도 트리에 남겨 둔다.
    expect(find.byType(IndexedStack), findsWidgets);
  });

  testWidgets('탭 바의 currentIndex가 현재 갈래를 따른다', (tester) async {
    // 없으면 hardcoded 0 같은 퇴행이 조용히 통과한다.
    await pump(tester);
    var tabBar = tester.widget<BottomTabBar>(
      find.descendant(
        of: find.byType(RoleShell),
        matching: find.byType(BottomTabBar),
      ),
    );
    expect(tabBar.currentIndex, 0, reason: '처음에는 홈(인덱스 0)');

    await tester.tap(find.text('숙제'));
    await tester.pumpAndSettle();
    tabBar = tester.widget<BottomTabBar>(
      find.descendant(
        of: find.byType(RoleShell),
        matching: find.byType(BottomTabBar),
      ),
    );
    expect(tabBar.currentIndex, 1, reason: '숙제 탭으로 이동 후(인덱스 1)');

    await tester.tap(find.text('수업'));
    await tester.pumpAndSettle();
    tabBar = tester.widget<BottomTabBar>(
      find.descendant(
        of: find.byType(RoleShell),
        matching: find.byType(BottomTabBar),
      ),
    );
    expect(tabBar.currentIndex, 2, reason: '수업 탭으로 이동 후(인덱스 2)');
  });
}
