import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/student/homeworks/student_homework_controllers.dart';
import 'package:academy_app/features/student/homeworks/student_homework_models.dart';
import 'package:academy_app/features/student/homeworks/student_homework_repository.dart';
import 'package:academy_app/features/student/homeworks/student_homeworks_page.dart';
import 'package:academy_app/shared/widgets/app_badge.dart';
import 'package:academy_app/shared/widgets/section.dart';

StudentHomeworkItem _hw({
  required int id,
  String kind = 'GRID',
  String status = 'NOT_SUBMITTED',
  String? result,
  int? rate,
  bool resubmitRequired = false,
  String? lessonDate = '2026-09-14',
  int? remaining,
  String? description,
  int photoCount = 0,
}) => StudentHomeworkItem(
  homeworkId: id,
  title: '숙제 $id',
  description: description,
  classRoomName: 'A반',
  kind: kind,
  lessonDate: lessonDate,
  result: result,
  completionRate: rate,
  resolvedByResubmission: false,
  resubmitRequired: resubmitRequired,
  dueAt: null,
  status: status,
  isLate: false,
  photoCount: photoCount,
  hasVideo: false,
  remainingMinutes: remaining,
);

class _Repo implements StudentHomeworkRepository {
  _Repo({required this.all, this.notesList = const [], this.byMonth});

  final List<StudentHomeworkItem> all;
  final List<HomeworkNote> notesList;
  final List<StudentHomeworkItem>? byMonth;
  final List<(int?, int?)> listCalls = [];

  @override
  Future<List<StudentHomeworkItem>> list({int? year, int? month}) async {
    listCalls.add((year, month));
    return year == null ? all : (byMonth ?? const []);
  }

  @override
  Future<List<HomeworkNote>> notes() async => notesList;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<_Repo> _pump(WidgetTester tester, _Repo repo) async {
  final c = StudentHomeworksController(repository: repo);
  addTearDown(c.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: StudentHomeworksPage(controller: c)),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

String _badgeOf(WidgetTester tester, int id) => tester
    .widgetList<AppBadge>(
      find.descendant(
        of: find.byKey(ValueKey('homework-$id')),
        matching: find.byType(AppBadge),
      ),
    )
    .first
    .label;

void main() {
  testWidgets('세 구획 — 글 · 다시 제출 필요 · 지난 숙제(수업일별)', (tester) async {
    // ListView 는 화면 밖 자식을 안 만든다 — 구획 전부가 보이게 세로를 늘린다.
    tester.view.physicalSize = const Size(390 * 3, 2400 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await _pump(
      tester,
      _Repo(
        all: [
          _hw(id: 1, resubmitRequired: true, result: 'PARTIAL', rate: 40),
          _hw(id: 2, kind: 'ONLINE', status: 'NOT_SUBMITTED', remaining: 90),
          _hw(id: 3, result: 'DONE'),
          _hw(id: 4, result: 'DONE', lessonDate: '2026-09-07'),
        ],
        notesList: const [
          HomeworkNote(
            lessonId: 1,
            lessonDate: '2026-09-21',
            classRoomName: 'A반',
            weekLabel: '9월 3주',
            homeworkNote: '단어 3과\n문법 p.12',
          ),
        ],
      ),
    );
    expect(find.byKey(const Key('notes-section')), findsOneWidget);
    // 줄바꿈이 선생님이 쓴 그대로다.
    expect(find.text('단어 3과\n문법 p.12'), findsOneWidget);
    expect(find.text('A반 · 9월 3주 · 2026-09-21 수업'), findsOneWidget);

    final todo = find.byKey(const Key('todo-section'));
    expect(todo, findsOneWidget);
    for (final id in [1, 2]) {
      expect(
        find.descendant(
          of: todo,
          matching: find.byKey(ValueKey('homework-$id')),
        ),
        findsOneWidget,
      );
    }
    // 할 일은 지난 숙제에 다시 나오지 않는다.
    expect(find.byKey(const ValueKey('homework-1')), findsOneWidget);
    // 지난 숙제는 최근 수업일이 위다.
    final heads = tester
        .widgetList<SectionHead>(find.byType(SectionHead))
        .map((h) => h.title)
        .toList();
    expect(heads, ['이번 주에 낼 것', '다시 제출 필요', '9월 14일 수업', '9월 7일 수업']);
  });

  testWidgets('⭕ 받은 GRID 는 status 가 NOT_SUBMITTED 여도 「완료」다 (4-2)', (
    tester,
  ) async {
    await _pump(tester, _Repo(all: [_hw(id: 1, result: 'DONE')]));
    expect(_badgeOf(tester, 1), '완료');
    expect(find.text('미제출'), findsNothing);
    expect(find.byKey(const Key('todo-section')), findsNothing);
  });

  testWidgets('채점 전 GRID 는 「미채점」이다 — 0% 가 아니다', (tester) async {
    await _pump(tester, _Repo(all: [_hw(id: 1)]));
    expect(_badgeOf(tester, 1), '미채점');
  });

  testWidgets('ONLINE 미제출은 남은 시간, 지나면 빨강', (tester) async {
    await _pump(
      tester,
      _Repo(
        all: [
          _hw(id: 1, kind: 'ONLINE', remaining: 90),
          _hw(id: 2, kind: 'ONLINE', remaining: -30),
        ],
      ),
    );
    expect(_badgeOf(tester, 1), '1시간 남음');
    expect(_badgeOf(tester, 2), '마감 30분 지남');
    final late = tester
        .widgetList<AppBadge>(
          find.descendant(
            of: find.byKey(const ValueKey('homework-2')),
            matching: find.byType(AppBadge),
          ),
        )
        .first;
    expect(late.tone, BadgeTone.danger);
  });

  testWidgets('GRID 는 마감이 없으면 남은 시간 줄이 없다 — 0 을 그리지 않는다', (tester) async {
    await _pump(
      tester,
      _Repo(
        all: [
          _hw(
            id: 1,
            resubmitRequired: true,
            result: 'NOT_DONE',
            remaining: 120,
          ),
          _hw(id: 2, resubmitRequired: true, result: 'NOT_DONE'),
        ],
      ),
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('homework-1')),
        matching: find.text('2시간 남음'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('homework-2')),
        matching: find.byKey(const Key('grid-remaining')),
      ),
      findsNothing,
    );
  });

  testWidgets('「내용 보기」는 상세 내용을 시트로 띄운다 (4-8)', (tester) async {
    await _pump(
      tester,
      _Repo(
        all: [
          _hw(
            id: 1,
            resubmitRequired: true,
            result: 'NOT_DONE',
            description: '3번 다시\n풀이 써 오기',
          ),
        ],
      ),
    );
    await tester.tap(find.byKey(const Key('show-description')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('description-sheet')), findsOneWidget);
    expect(find.text('3번 다시\n풀이 써 오기'), findsOneWidget);
    await tester.tap(find.text('닫기'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('description-sheet')), findsNothing);
  });

  testWidgets('달을 고르면 연·월로 부르고, 할 일은 달과 무관하게 남는다', (tester) async {
    final repo = await _pump(
      tester,
      _Repo(
        all: [_hw(id: 1, kind: 'ONLINE')],
        byMonth: [_hw(id: 5, result: 'DONE', lessonDate: '2026-08-10')],
      ),
    );
    // 「전체 월」이면 목록 요청이 하나다(할 일과 같은 요청).
    expect(repo.listCalls, [(null, null)]);
    await tester.tap(find.byKey(const Key('filter-month')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('8월').last);
    await tester.pumpAndSettle();
    expect(repo.listCalls.last, (DateTime.now().year, 8));
    expect(find.byKey(const Key('todo-section')), findsOneWidget);
    expect(find.byKey(const ValueKey('homework-1')), findsOneWidget);
    expect(find.byKey(const ValueKey('homework-5')), findsOneWidget);
  });

  testWidgets('수업일 칩을 고르면 그 날만 남는다', (tester) async {
    await _pump(
      tester,
      _Repo(
        all: [
          _hw(id: 1, result: 'DONE', lessonDate: '2026-09-14'),
          _hw(id: 2, result: 'DONE', lessonDate: '2026-09-07'),
        ],
      ),
    );
    await tester.tap(find.byKey(const Key('day-chip-9/7')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('homework-2')), findsOneWidget);
    expect(find.byKey(const ValueKey('homework-1')), findsNothing);
  });

  testWidgets('아무것도 없으면 「받은 숙제가 없습니다」', (tester) async {
    await _pump(tester, _Repo(all: const []));
    expect(find.text('숙제'), findsOneWidget); // 화면이 그려졌다(14-12)
    expect(find.text('받은 숙제가 없습니다.'), findsOneWidget);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360 * 3, 800 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await _pump(
      tester,
      _Repo(
        all: [
          StudentHomeworkItem(
            homeworkId: 1,
            title: '아주 긴 숙제 제목이 한 줄을 넘어가도 배지를 밀어내지 않는다 ' * 2,
            description: '내용',
            classRoomName: '아주 긴 반 이름 A고 2학년 목요일 저녁반',
            kind: 'GRID',
            lessonDate: '2026-09-14',
            result: 'PARTIAL',
            completionRate: 55,
            resolvedByResubmission: false,
            resubmitRequired: true,
            dueAt: null,
            status: 'SUBMITTED',
            isLate: true,
            photoCount: 10,
            hasVideo: true,
            remainingMinutes: 5000,
          ),
        ],
      ),
    );
    expect(find.byKey(const ValueKey('homework-1')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
