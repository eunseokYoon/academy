import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/router/auth_redirect.dart';
import 'package:academy_app/core/router/routes.dart';
import 'package:academy_app/features/student/home/student_home_controller.dart';
import 'package:academy_app/features/student/home/student_home_models.dart';
import 'package:academy_app/features/student/home/student_home_page.dart';
import 'package:academy_app/features/student/home/student_home_repository.dart';
import 'package:academy_app/shared/lib/home_labels.dart';
import 'package:academy_app/shared/widgets/app_bar_band.dart';
import 'package:academy_app/shared/widgets/bottom_tab_bar.dart';
import 'package:academy_app/shared/widgets/notice_card.dart';
import 'package:academy_app/shared/widgets/role_shell.dart';
import 'package:academy_app/shared/widgets/sub_page.dart';

/// 실제 셸(RoleShell + 실제 학생 홈)의 기하. 웹 `StudentLayout` 과 같은
/// 모양인지 — 앱바가 페이지와 함께 스크롤되고, 하단 바가 고정·반투명이라
/// 본문이 그 밑을 흐르고, 마지막 카드가 바에 딱 안 가릴 만큼만 여백이
/// 있는지 — 를 잰다.
///
/// 390×844 에 아이폰(노치 47, 홈 인디케이터 34)의 안전 영역을 준다.
/// 안전 영역이 0 이면 탭 바 높이 계산이 틀려도 드러나지 않는다.

class _Repo implements StudentHomeRepository {
  @override
  Future<StudentHome> fetch() async => StudentHome(
    studentName: '김하늘',
    nextLesson: const NextLesson(
      lessonDate: '2026-09-21',
      startTime: '19:00',
      dDay: 2,
      classRoomName: 'A고 2학년 목요일반',
    ),
    nextExam: null,
    nextClinic: null,
    // 화면보다 길어야 끝까지 굴렸을 때의 틈을 잴 수 있다.
    currentHomeworks: [
      for (var i = 0; i < 6; i++)
        HomeHomework(
          homeworkId: i,
          title: '숙제 $i',
          dueAt: '2026-09-21T21:00:00+09:00',
          status: 'NOT_SUBMITTED',
          remainingMinutes: 100,
        ),
    ],
    lastLesson: null,
    notices: const HomeNotices(totalCount: 0, recent: []),
  );
}

GoRouter _router(StudentHomeController c) => GoRouter(
  initialLocation: AppRoutes.student,
  routes: [
    StatefulShellRoute.indexedStack(
      builder: (_, _, shell) =>
          RoleShell(role: '학생', tabs: kStudentTabs, navigationShell: shell),
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.student,
              builder: (_, _) => StudentHomePage(controller: c),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.studentHomeworks,
              builder: (_, _) => const SizedBox.shrink(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.studentLessons,
              builder: (_, _) => const SizedBox.shrink(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.studentScores,
              builder: (_, _) => const SizedBox.shrink(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.studentQna,
              // 하위 화면의 틀(SubPageScroll)을 이 탭 자리에 둬서 셸 안
              // 하위 화면의 기하를 잰다. 길어야 끝까지 굴렸을 때를 잴 수 있다.
              builder: (_, _) => SubPageScroll(
                role: '학생',
                children: [
                  for (var i = 0; i < 20; i++)
                    SizedBox(height: 80, child: Text('줄 $i')),
                  const SizedBox(
                    key: Key('sub-last'),
                    height: 40,
                    child: Text('마지막'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ],
    ),
  ],
);

/// 틈의 상한. 목표는 `kHomeTailGap`(웹 pb-24 − 탭 바 ≈ 39)이다.
const double _maxGap = 48;

void main() {
  late GoRouter router;

  Future<void> pumpShell(WidgetTester tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1.0;
    tester.view.padding = const FakeViewPadding(top: 47, bottom: 34);
    tester.view.viewPadding = const FakeViewPadding(top: 47, bottom: 34);
    addTearDown(tester.view.reset);
    router = _router(StudentHomeController(repository: _Repo()));
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.pumpAndSettle();
  }

  Rect homeViewport(WidgetTester tester) {
    final vp = tester.renderObject<RenderBox>(
      find
          .descendant(
            of: find.byType(StudentHomePage),
            matching: find.byType(Viewport),
          )
          .first,
    );
    return vp.localToGlobal(Offset.zero) & vp.size;
  }

  double tabBarTop(WidgetTester tester) =>
      tester.getRect(find.byType(BottomTabBar)).top;

  testWidgets('(a) 처음 화면에서 날짜 줄이 목록 뷰포트 안에 통째로 보인다', (tester) async {
    // 앱바를 셸에 고정하면 목록 뷰포트가 앱바 아래에서 시작하고, 지면의
    // -40 이 날짜 줄을 그 위로 끌어올려 잘린다(y 46–65 가 전부 안 보였다).
    await pumpShell(tester);
    final vp = homeViewport(tester);
    final date = tester.getRect(find.text(todayLabel()));
    // ignore: avoid_print
    print('geometry(a): date line=$date viewport=$vp');
    expect(
      date.top,
      greaterThanOrEqualTo(vp.top),
      reason: '날짜 $date / 뷰포트 $vp',
    );
    expect(date.bottom, lessThanOrEqualTo(vp.bottom));
    // 앱바가 화면 맨 위, 스크롤 안에 있다.
    expect(
      find.descendant(
        of: find.byType(StudentHomePage),
        matching: find.byType(AppBarBand),
      ),
      findsOneWidget,
    );
    expect(tester.getRect(find.byType(AppBarBand)).top, 0);
  });

  testWidgets('(b) 끝까지 굴리면 마지막 카드가 탭 바 위에서 틈 하나만큼 떨어진다', (tester) async {
    await pumpShell(tester);
    await tester.drag(find.byType(ListView).first, const Offset(0, -3000));
    await tester.pumpAndSettle();

    final last = tester.getRect(find.byType(NoticeCard));
    final barTop = tabBarTop(tester);
    final gap = barTop - last.bottom;
    // ignore: avoid_print
    print(
      'geometry(b): last card bottom=${last.bottom} tabBarTop=$barTop gap=$gap',
    );
    expect(gap, greaterThanOrEqualTo(0), reason: '마지막 카드가 탭 바에 가린다');
    expect(gap, lessThan(_maxGap), reason: '마지막 카드 밑이 $gap px 비었다');
  });

  testWidgets('(c) 본문이 탭 바 밑까지 내려간다(바의 블러가 보인다)', (tester) async {
    await pumpShell(tester);
    final vp = homeViewport(tester);
    final barTop = tabBarTop(tester);
    // ignore: avoid_print
    print('geometry(c): viewport=$vp tabBarTop=$barTop');
    expect(vp.bottom, greaterThan(barTop));
    expect(vp.bottom, 844);
  });

  testWidgets('하위 화면은 자기 앱바를 그리고 마지막 줄이 탭 바에 가리지 않는다', (tester) async {
    await pumpShell(tester);
    await tester.tap(find.text('질문').last);
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byType(SubPageScroll),
        matching: find.byType(AppBarBand),
      ),
      findsOneWidget,
    );
    // 끝까지 굴린다. ListView 는 자식을 늦게 그려서 끝으로 한 번 보내면 그 뒤에 끝이 더
    // 늘어난다 — 늘지 않을 때까지 되풀이한다.
    final pos = tester
        .state<ScrollableState>(
          find
              .descendant(
                of: find.byType(SubPageScroll),
                matching: find.byType(Scrollable),
              )
              .first,
        )
        .position;
    for (var i = 0; i < 10 && pos.pixels < pos.maxScrollExtent; i++) {
      pos.jumpTo(pos.maxScrollExtent);
      await tester.pumpAndSettle();
    }
    final last = tester.getRect(find.byKey(const Key('sub-last')));
    final barTop = tabBarTop(tester);
    expect(last.bottom, lessThanOrEqualTo(barTop));
    // 틈이 과하지도 않다(예전 고정값 176 은 바를 두 번 셌다).
    expect(barTop - last.bottom, lessThanOrEqualTo(_maxGap));
  });
}
