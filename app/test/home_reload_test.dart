import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/router/auth_redirect.dart';
import 'package:academy_app/core/router/routes.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/home/parent_home_controller.dart';
import 'package:academy_app/features/parent/home/parent_home_models.dart';
import 'package:academy_app/features/parent/home/parent_home_page.dart';
import 'package:academy_app/features/parent/home/parent_home_repository.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/features/student/home/student_home_controller.dart';
import 'package:academy_app/features/student/home/student_home_models.dart';
import 'package:academy_app/features/student/home/student_home_page.dart';
import 'package:academy_app/features/student/home/student_home_repository.dart';
import 'package:academy_app/shared/widgets/role_shell.dart';

/// 60초 규칙이 **실제로 불리는지.** 컨트롤러는 60초 안이면 무시하지만,
/// 그 `load()` 를 부르는 쪽이 없으면 규칙은 장식이다 — 셸이 indexedStack 이라
/// 홈의 State 가 살아 있어 initState 는 다시 안 온다.
///
/// 부르는 때는 둘이다: 홈이 다시 보일 때(다른 탭에서 돌아옴), 앱이
/// 포그라운드로 돌아올 때. 시계는 주입한다.

class _StudentRepo implements StudentHomeRepository {
  int calls = 0;

  @override
  Future<StudentHome> fetch() async {
    calls++;
    return const StudentHome(
      studentName: '김하늘',
      nextLesson: null,
      nextExam: null,
      nextClinic: null,
      currentHomeworks: [],
      lastLesson: null,
      notices: HomeNotices(totalCount: 0, recent: []),
    );
  }
}

class _ParentRepo implements ParentHomeRepository {
  int calls = 0;

  @override
  Future<ParentHome> fetch(int studentId) async {
    calls++;
    return ParentHome.fromJson({
      'student': {'name': '김하늘'},
      'nextExam': null,
      'nextLessonDate': null,
      'nextLessonTime': null,
      'nextLessonDDay': null,
      'notices': {'totalCount': 0, 'recent': []},
      'pendingHomeworkCount': 0,
      'nextClinic': null,
      'thisMonthAttendance': {
        'present': 0,
        'late': 0,
        'absent': 0,
        'sick': 0,
        'excused': 0,
        'makeup': 0,
      },
    });
  }
}

/// 자녀 목록 하나만 답한다.
class _ChildrenAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode({
      'success': true,
      'data': [
        {'studentId': 1, 'name': '김하늘'},
      ],
    }),
    200,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );

  @override
  void close({bool force = false}) {}
}

/// 홈 갈래 + 나머지 네 갈래(글자만).
GoRouter _router({
  required String homePath,
  required List<BottomTab> tabs,
  required String role,
  required Widget Function() home,
}) => GoRouter(
  initialLocation: homePath,
  routes: [
    StatefulShellRoute.indexedStack(
      builder: (_, _, shell) =>
          RoleShell(role: role, tabs: tabs, navigationShell: shell),
      branches: [
        StatefulShellBranch(
          routes: [GoRoute(path: homePath, builder: (_, _) => home())],
        ),
        for (final t in tabs.skip(1))
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

Future<void> _resume(WidgetTester tester) async {
  for (final s in const [
    AppLifecycleState.inactive,
    AppLifecycleState.hidden,
    AppLifecycleState.paused,
    AppLifecycleState.hidden,
    AppLifecycleState.inactive,
    AppLifecycleState.resumed,
  ]) {
    tester.binding.handleAppLifecycleStateChanged(s);
  }
  await tester.pumpAndSettle();
}

void main() {
  group('학생 홈', () {
    late _StudentRepo repo;
    late DateTime now;

    Future<void> pump(WidgetTester tester) async {
      repo = _StudentRepo();
      now = DateTime(2026, 9, 21, 10);
      final c = StudentHomeController(repository: repo, now: () => now);
      addTearDown(c.dispose);
      await tester.pumpWidget(
        MaterialApp.router(
          routerConfig: _router(
            homePath: AppRoutes.student,
            tabs: kStudentTabs,
            role: '학생',
            home: () => StudentHomePage(controller: c),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(repo.calls, 1);
    }

    Future<void> leaveHome(WidgetTester tester) async {
      await tester.tap(
        find.byKey(const ValueKey('tab-${AppRoutes.studentHomeworks}')),
      );
      await tester.pumpAndSettle();
      expect(find.text('화면:숙제'), findsOneWidget);
    }

    Future<void> backHome(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('tab-${AppRoutes.student}')));
      await tester.pumpAndSettle();
    }

    testWidgets('탭을 떠났다 60초 넘어 돌아오면 다시 부른다', (tester) async {
      await pump(tester);
      await leaveHome(tester);
      now = now.add(const Duration(seconds: 61));
      await backHome(tester);
      expect(repo.calls, 2);
    });

    testWidgets('60초 안에 돌아오면 부르지 않는다', (tester) async {
      await pump(tester);
      await leaveHome(tester);
      now = now.add(const Duration(seconds: 30));
      await backHome(tester);
      expect(repo.calls, 1);
    });

    testWidgets('앱을 60초 넘어 다시 올리면 부른다', (tester) async {
      await pump(tester);
      now = now.add(const Duration(seconds: 61));
      await _resume(tester);
      expect(repo.calls, 2);
    });

    testWidgets('앱을 60초 안에 다시 올리면 부르지 않는다', (tester) async {
      await pump(tester);
      now = now.add(const Duration(seconds: 30));
      await _resume(tester);
      expect(repo.calls, 1);
    });
  });

  group('학부모 홈', () {
    late _ParentRepo repo;
    late DateTime now;

    Future<void> pump(WidgetTester tester) async {
      repo = _ParentRepo();
      now = DateTime(2026, 9, 21, 10);
      final c = ParentHomeController(repository: repo, now: () => now);
      final sc = SelectedChild(
        dio: Dio(BaseOptions(baseUrl: 'https://example.test'))
          ..httpClientAdapter = _ChildrenAdapter(),
        store: InMemoryKeyValueStore(),
      );
      addTearDown(c.dispose);
      addTearDown(sc.dispose);
      await tester.pumpWidget(
        MaterialApp.router(
          routerConfig: _router(
            homePath: AppRoutes.parent,
            tabs: kParentTabs,
            role: '학부모',
            home: () => ParentHomePage(controller: c, selectedChild: sc),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(repo.calls, 1);
    }

    testWidgets('탭을 떠났다 60초 넘어 돌아오면 고른 자녀를 다시 부른다', (tester) async {
      await pump(tester);
      await tester.tap(find.byKey(ValueKey('tab-${kParentTabs[1].route}')));
      await tester.pumpAndSettle();
      now = now.add(const Duration(seconds: 61));
      await tester.tap(find.byKey(ValueKey('tab-${kParentTabs[0].route}')));
      await tester.pumpAndSettle();
      expect(repo.calls, 2);
    });

    testWidgets('앱을 60초 넘어 다시 올리면 부른다', (tester) async {
      await pump(tester);
      now = now.add(const Duration(seconds: 61));
      await _resume(tester);
      expect(repo.calls, 2);
    });
  });
}
