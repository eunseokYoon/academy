import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/features/student/online_tests/online_test_data.dart';
import 'package:academy_app/features/student/online_tests/student_online_test_page.dart';
import 'package:academy_app/features/student/online_tests/student_online_tests_page.dart';

OnlineTestListItem _item(
  int id,
  String status, {
  int? remaining = 120,
  int answered = 0,
}) => OnlineTestListItem(
  testId: id,
  title: '테스트 $id',
  classRoomName: 'A반',
  questionCount: 5,
  remainingMinutes: remaining,
  status: status,
  answeredCount: answered,
);

OnlineTestTake _take({
  String status = 'IN_PROGRESS',
  String? closesAt = '2026-09-27T21:00:00+09:00',
  List<int?> chosen = const [2, null, null],
}) => OnlineTestTake(
  testId: 7,
  title: '9월 2주 클리닉',
  classRoomName: 'A반',
  questionCount: 3,
  choiceCount: 5,
  closesAt: closesAt,
  chosenChoices: chosen,
  status: status,
);

const _split = OnlineTestResult(
  title: '9월 2주 클리닉',
  correctCount: 2,
  questionCount: 3,
  internalQuestionCount: 2,
  internalCorrect: 1,
  externalCorrect: 1,
  answerFileUrls: ['https://s3.test/answer.pdf'],
  results: [
    QuestionResult(questionNo: 1, chosen: 2, correct: 2, isCorrect: true),
    QuestionResult(questionNo: 2, chosen: null, correct: 3, isCorrect: false),
    QuestionResult(questionNo: 3, chosen: 4, correct: 4, isCorrect: true),
  ],
);

class _Repo implements OnlineTestRepository {
  _Repo({
    this.items = const [],
    OnlineTestTake? take,
    this.resultValue = _split,
    this.saveError,
  }) : takeValue = take ?? _take();

  List<OnlineTestListItem> items;
  OnlineTestTake takeValue;
  OnlineTestResult resultValue;
  Object? saveError;

  /// 부른 순서. 제출 전에 저장이 먼저 나갔는지 본다.
  final List<String> log = [];
  final List<List<int?>> saves = [];

  @override
  Future<List<OnlineTestListItem>> list() async => items;

  @override
  Future<OnlineTestTake> take(int testId) async {
    log.add('take');
    return takeValue;
  }

  @override
  Future<void> saveAnswers(int testId, List<int?> chosenChoices) async {
    log.add('save');
    final e = saveError;
    if (e != null) throw e;
    saves.add(chosenChoices);
  }

  @override
  Future<OnlineTestResult> submit(int testId) async {
    log.add('submit');
    return resultValue;
  }

  @override
  Future<OnlineTestResult> result(int testId) async {
    log.add('result');
    return resultValue;
  }
}

Future<GoRouter> _pump(WidgetTester tester, Widget page) async {
  tester.view.physicalSize = const Size(360 * 3, 2400 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final router = GoRouter(
    initialLocation: '/start',
    routes: [
      GoRoute(
        path: '/start',
        builder: (_, _) => Scaffold(body: page),
      ),
      GoRoute(
        path: '/student/online-tests',
        builder: (_, _) => const Text('목록 화면'),
        routes: [
          GoRoute(
            path: ':id',
            builder: (_, s) => Text('응시 ${s.pathParameters['id']}'),
          ),
        ],
      ),
    ],
  );
  addTearDown(router.dispose);
  await tester.pumpWidget(MaterialApp.router(routerConfig: router));
  await tester.pumpAndSettle();
  return router;
}

/// 마감(21:00 KST) 한 시간 전.
final _before = DateTime.utc(2026, 9, 27, 11);

/// 마감 1분 뒤.
final _after = DateTime.utc(2026, 9, 27, 12, 1);

class _Harness {
  _Harness(this.repo, this.controller, this.opened, this.submitted);

  final _Repo repo;
  final OnlineTestTakeController controller;
  final List<Uri> opened;
  final List<int> submitted;
}

Future<_Harness> _pumpTake(
  WidgetTester tester,
  _Repo repo, {
  DateTime? now,
}) async {
  final c = OnlineTestTakeController(repository: repo);
  addTearDown(c.dispose);
  final opened = <Uri>[];
  final submitted = <int>[];
  await _pump(
    tester,
    StudentOnlineTestPage(
      testId: 7,
      controller: c,
      openUrl: (u) async {
        opened.add(u);
        return true;
      },
      onSubmitted: () => submitted.add(1),
      now: () => now ?? _before,
    ),
  );
  return _Harness(repo, c, opened, submitted);
}

Future<void> _confirmSubmit(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('test-submit')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('confirm-submit')));
  await tester.pumpAndSettle();
}

void main() {
  group('해설지 여러 장(2026-09-29)', () {
    Map<String, dynamic> j(Map<String, dynamic> extra) => {
      'title': 't',
      'correctCount': 1,
      'questionCount': 2,
      'results': [],
      ...extra,
    };

    test('answerFileUrls 를 전부 읽는다', () {
      final r = OnlineTestResult.fromJson(
        j({
          'answerFileUrl': 'https://a',
          'answerFileUrls': ['https://a', 'https://b'],
        }),
      );
      expect(r.answerFileUrls, ['https://a', 'https://b']);
    });

    test('옛 서버(answerFileUrls 없음)는 한 장으로 받는다(7-3)', () {
      expect(
        OnlineTestResult.fromJson(j({'answerFileUrl': 'https://a'}))
            .answerFileUrls,
        ['https://a'],
      );
      expect(
        OnlineTestResult.fromJson(j({'answerFileUrl': null})).answerFileUrls,
        isEmpty,
      );
    });
  });

  group('데이터', () {
    test('남은 분은 마감과의 차를 반올림한다(웹 Math.round)', () {
      const closes = '2026-09-27T21:00:00+09:00';
      expect(remainingMinutesOf(closes, DateTime.utc(2026, 9, 27, 11)), 60);
      expect(
        remainingMinutesOf(closes, DateTime.utc(2026, 9, 27, 11, 59, 15)),
        1,
      );
      expect(remainingMinutesOf(closes, _after), -1);
      expect(remainingMinutesOf(null, _after), isNull);
    });

    test('임시 저장된 답은 문항 수 길이로 맞춘다', () {
      final take = OnlineTestTake.fromJson({
        'testId': 1,
        'title': 't',
        'classRoomName': 'A반',
        'questionCount': 3,
        'choiceCount': 5,
        'closesAt': null,
        'chosenChoices': [1],
        'status': 'IN_PROGRESS',
      });
      expect(take.chosenChoices, [1, null, null]);
    });

    test('오프라인 응시는 끝난 것이다', () {
      expect(isTakeFinished('OFFLINE'), isTrue);
      expect(isTakeFinished('SUBMITTED'), isTrue);
      expect(isTakeFinished('IN_PROGRESS'), isFalse);
      expect(isTakeFinished('NOT_STARTED'), isFalse);
    });
  });

  group('목록', () {
    Future<GoRouter> pumpList(WidgetTester tester, _Repo repo) {
      final c = OnlineTestListController(repository: repo);
      addTearDown(c.dispose);
      return _pump(tester, StudentOnlineTestsPage(controller: c));
    }

    testWidgets('안 낸 것만 주황 구획이고, 오프라인 응시는 제출 완료 쪽이다', (tester) async {
      await pumpList(
        tester,
        _Repo(
          items: [
            _item(1, 'NOT_STARTED'),
            _item(2, 'IN_PROGRESS', answered: 3),
            _item(3, 'SUBMITTED'),
            _item(4, 'OFFLINE'),
          ],
        ),
      );
      expect(find.text('응시할 테스트'), findsOneWidget);
      final todo = find.byKey(const Key('tests-todo'));
      final done = find.byKey(const Key('tests-done'));
      for (final id in [1, 2]) {
        expect(
          find.descendant(of: todo, matching: find.byKey(ValueKey('test-$id'))),
          findsOneWidget,
        );
      }
      for (final id in [3, 4]) {
        expect(
          find.descendant(of: done, matching: find.byKey(ValueKey('test-$id'))),
          findsOneWidget,
        );
      }
      expect(find.text('오프라인 응시'), findsOneWidget);
      expect(find.text('3 / 5 입력'), findsOneWidget);
      // 끝난 것에는 남은 시간을 안 붙인다.
      expect(find.text('2시간 남음'), findsNWidgets(2));
    });

    testWidgets('마감 지남은 서버 값으로 쓰고 빨강이 아니다', (tester) async {
      await pumpList(
        tester,
        _Repo(items: [_item(1, 'NOT_STARTED', remaining: -90)]),
      );
      final label = tester.widget<Text>(find.text('마감 1시간 지남'));
      expect(label.style!.color, AppColors.amber700);
    });

    testWidgets('없으면 안내하고, 누르면 응시 화면으로 간다', (tester) async {
      await pumpList(tester, _Repo());
      expect(find.text('온라인 테스트'), findsOneWidget);
      expect(find.text('응시할 테스트가 없습니다.'), findsOneWidget);
    });

    testWidgets('줄을 누르면 응시 화면으로 간다', (tester) async {
      final router = await pumpList(
        tester,
        _Repo(items: [_item(9, 'NOT_STARTED')]),
      );
      await tester.tap(find.byKey(const ValueKey('test-9')));
      await tester.pumpAndSettle();
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        '/student/online-tests/9',
      );
    });
  });

  group('응시', () {
    testWidgets('저장된 답으로 채우고, 고르면 잠시 뒤 한 번에 저장한다', (tester) async {
      final h = await _pumpTake(tester, _Repo());
      expect(find.text('9월 2주 클리닉'), findsOneWidget);
      expect(find.text('1 / 3 입력'), findsOneWidget);
      expect(find.text('1시간 남음'), findsOneWidget);
      expect(find.text('2문항이 비어 있습니다. 미체크는 오답으로 처리됩니다.'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('q-1-3')));
      await tester.pump(const Duration(milliseconds: 500));
      await tester.tap(find.byKey(const ValueKey('q-2-5')));
      await tester.pump(const Duration(milliseconds: 1000));
      // 아직 1.5초가 안 지났다 — 마지막 탭부터 다시 센다.
      expect(h.repo.saves, isEmpty);
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pumpAndSettle();
      expect(h.repo.saves, [
        [2, 3, 5],
      ]);
      expect(find.byKey(const Key('saved-at')), findsOneWidget);
      expect(find.text('3 / 3 입력'), findsOneWidget);
    });

    testWidgets('같은 번호를 다시 누르면 선택을 푼다', (tester) async {
      final h = await _pumpTake(tester, _Repo());
      await tester.tap(find.byKey(const ValueKey('q-0-2')));
      await tester.pump(kAutosaveDelay);
      await tester.pumpAndSettle();
      expect(h.repo.saves.single, [null, null, null]);
    });

    testWidgets('제출은 남은 답을 먼저 저장하고, 결과를 보여준다', (tester) async {
      final h = await _pumpTake(tester, _Repo());
      await tester.tap(find.byKey(const ValueKey('q-1-3')));
      await tester.pump();
      await _confirmSubmit(tester);

      expect(h.repo.log, ['take', 'save', 'submit']);
      expect(h.repo.saves.single, [2, 3, null]);
      expect(h.submitted, [1]);
      expect(find.byKey(const Key('result-header')), findsOneWidget);
      expect(find.text('내부 1/2'), findsOneWidget);
      expect(find.text('외부 1/1'), findsOneWidget);
      expect(find.text('정답 2'), findsOneWidget);
      expect(find.text('미체크 → 정답 3'), findsOneWidget);
      // 환산 점수는 없다(2026-09-10).
      expect(find.textContaining('점'), findsNothing);

      await tester.tap(find.byKey(const Key('answer-file')));
      expect(h.opened.single.toString(), 'https://s3.test/answer.pdf');
    });

    testWidgets('제출을 취소하면 아무것도 안 나간다', (tester) async {
      final h = await _pumpTake(tester, _Repo());
      await tester.tap(find.byKey(const Key('test-submit')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('취소'));
      await tester.pumpAndSettle();
      expect(h.repo.log, ['take']);
    });

    testWidgets('답 저장이 실패하면 제출하지 않고 이유를 보여준다', (tester) async {
      final h = await _pumpTake(
        tester,
        _Repo(
          saveError: const ApiException(code: 'X', message: '마감이 지났습니다.'),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('q-1-3')));
      await tester.pump();
      await _confirmSubmit(tester);
      expect(h.repo.log, ['take', 'save']);
      expect(find.text('마감이 지났습니다.'), findsWidgets);
      expect(h.submitted, isEmpty);
    });

    testWidgets('기기 시계로 마감이 지났으면 입력칸을 숨긴다', (tester) async {
      await _pumpTake(tester, _Repo(), now: _after);
      expect(find.text('9월 2주 클리닉'), findsOneWidget);
      expect(find.byKey(const Key('test-closed')), findsOneWidget);
      expect(find.byKey(const ValueKey('q-0')), findsNothing);
      expect(find.byKey(const Key('test-submit')), findsNothing);
      expect(find.text('마감 1분 지남'), findsOneWidget);
    });

    testWidgets('마감이 없으면 남은 시간을 안 그린다', (tester) async {
      await _pumpTake(tester, _Repo(take: _take(closesAt: null)));
      expect(find.text('9월 2주 클리닉'), findsOneWidget);
      expect(find.byKey(const Key('test-remaining')), findsNothing);
      expect(find.byKey(const ValueKey('q-0')), findsOneWidget);
    });

    testWidgets('이미 냈으면 결과를 바로 불러온다', (tester) async {
      const plain = OnlineTestResult(
        title: '단어',
        correctCount: 2,
        questionCount: 3,
        internalQuestionCount: null,
        internalCorrect: null,
        externalCorrect: null,
        answerFileUrls: [],
        results: [],
      );
      final h = await _pumpTake(
        tester,
        _Repo(
          take: _take(status: 'SUBMITTED'),
          resultValue: plain,
        ),
      );
      expect(h.repo.log, ['take', 'result']);
      expect(find.text('2 / 3'), findsOneWidget);
      expect(find.byKey(const Key('answer-file')), findsNothing);
      expect(find.byKey(const Key('test-submit')), findsNothing);
    });

    testWidgets('저장 전에 화면을 떠나면 남은 답을 저장한다', (tester) async {
      final h = await _pumpTake(tester, _Repo());
      await tester.tap(find.byKey(const ValueKey('q-2-1')));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      expect(h.repo.saves.single, [2, null, 1]);
    });
  });
}
