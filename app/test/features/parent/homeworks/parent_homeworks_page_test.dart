import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/homeworks/parent_homework_data.dart';
import 'package:academy_app/features/parent/homeworks/parent_homeworks_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/shared/widgets/app_badge.dart';
import 'package:academy_app/shared/widgets/lesson_day_filter.dart';

ParentHomework _hw({
  required int id,
  String kind = 'GRID',
  String status = 'NOT_SUBMITTED',
  String? result,
  int photoCount = 0,
}) => ParentHomework(
  homeworkId: id,
  title: '숙제 $id',
  classRoomName: 'A반',
  kind: kind,
  lessonDate: '2026-09-14',
  result: result,
  completionRate: null,
  resolvedByResubmission: false,
  dueAt: null,
  status: status,
  isLate: false,
  photoCount: photoCount,
);

class _Repo implements ParentHomeworkRepository {
  _Repo(this.byChild);

  final Map<int, List<ParentHomework>> byChild;
  final List<(int, MonthFilter)> listCalls = [];
  final List<(int, int)> photoCalls = [];

  @override
  Future<List<ParentHomework>> list(int studentId, MonthFilter filter) async {
    listCalls.add((studentId, filter));
    return byChild[studentId] ?? const [];
  }

  @override
  Future<List<String>> photos(int studentId, int homeworkId) async {
    photoCalls.add((studentId, homeworkId));
    return ['https://p/1', 'https://p/2'];
  }
}

SelectedChild _children(List<(int, String)> kids) {
  final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
    ..httpClientAdapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': [
              for (final (id, name) in kids) {'studentId': id, 'name': name},
            ],
          },
        ),
      ],
    );
  return SelectedChild(dio: dio, store: InMemoryKeyValueStore());
}

Future<(_Repo, SelectedChild)> _pump(
  WidgetTester tester,
  _Repo repo, {
  List<(int, String)> kids = const [(1, '김하늘')],
}) async {
  tester.view.physicalSize = const Size(390 * 3, 1600 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final sc = _children(kids);
  final c = ParentHomeworksController(repository: repo);
  addTearDown(c.dispose);
  addTearDown(sc.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: ParentHomeworksPage(controller: c, selectedChild: sc),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (repo, sc);
}

void main() {
  testWidgets('자녀 목록을 받고 첫째의 숙제를 부른다', (tester) async {
    final (repo, _) = await _pump(
      tester,
      _Repo({
        1: [_hw(id: 1, result: 'DONE')],
      }),
    );
    expect(repo.listCalls, [(1, MonthFilter.all)]);
    expect(find.text('숙제 제출 현황'), findsOneWidget);
    expect(find.text('9월 14일 수업'), findsOneWidget);
  });

  testWidgets('GRID 는 채점축, ONLINE 은 「제출」·「미제출」이다', (tester) async {
    await _pump(
      tester,
      _Repo({
        1: [
          // ⭕ GRID — status 는 NOT_SUBMITTED 지만 「미제출」이면 안 된다(4-2).
          _hw(id: 1, result: 'DONE'),
          _hw(id: 2, kind: 'ONLINE'),
          _hw(id: 3, kind: 'ONLINE', status: 'SUBMITTED'),
        ],
      }),
    );
    List<String> labels(int id) => tester
        .widgetList<AppBadge>(
          find.descendant(
            of: find.byKey(ValueKey('parent-homework-$id')),
            matching: find.byType(AppBadge),
          ),
        )
        .map((b) => b.label)
        .toList();
    expect(labels(1), ['완료']);
    expect(labels(2), ['미제출']);
    expect(labels(3), ['제출']);
  });

  testWidgets('사진이 있는 줄만 눌리고, 누르면 사진만 보여준다 (4-6)', (tester) async {
    final (repo, _) = await _pump(
      tester,
      _Repo({
        1: [
          _hw(id: 1, result: 'DONE', photoCount: 2),
          _hw(id: 2, result: 'DONE'),
        ],
      }),
    );
    await tester.tap(find.byKey(const ValueKey('parent-homework-2')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('photo-sheet')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('parent-homework-1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('photo-sheet')), findsOneWidget);
    expect(repo.photoCalls, [(1, 1)]);
    expect(
      find.byKey(const ValueKey('sheet-photo-https://p/1')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('sheet-photo-https://p/2')),
      findsOneWidget,
    );
  });

  testWidgets('자녀가 하나면 제목 줄에 자녀 선택이 없다', (tester) async {
    await _pump(tester, _Repo({1: []}));
    expect(find.text('숙제 제출 현황'), findsOneWidget);
    expect(find.byKey(const Key('title-child-select')), findsNothing);
  });

  testWidgets('자녀를 바꾸면 그 아이의 숙제를 부른다', (tester) async {
    final (repo, _) = await _pump(
      tester,
      _Repo({
        1: [_hw(id: 1, result: 'DONE')],
        2: [_hw(id: 9, result: 'NOT_DONE')],
      }),
      kids: const [(1, '김하늘'), (2, '김바다')],
    );
    expect(find.byKey(const Key('title-child-select')), findsOneWidget);
    await tester.tap(find.byKey(const Key('title-child-select')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('김바다').last);
    await tester.pumpAndSettle();
    expect(repo.listCalls.last, (2, MonthFilter.all));
    expect(find.byKey(const ValueKey('parent-homework-9')), findsOneWidget);
    expect(find.byKey(const ValueKey('parent-homework-1')), findsNothing);
  });

  testWidgets('숙제 내용·영상을 그릴 자리가 없다', (tester) async {
    await _pump(
      tester,
      _Repo({
        1: [_hw(id: 1, result: 'DONE', photoCount: 1)],
      }),
    );
    expect(find.byKey(const ValueKey('parent-homework-1')), findsOneWidget);
    expect(find.text('내용 보기'), findsNothing);
    expect(find.text('영상'), findsNothing);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    await _pump(
      tester,
      _Repo({
        1: [
          ParentHomework(
            homeworkId: 1,
            title: '아주 긴 숙제 제목 ' * 6,
            classRoomName: '아주 긴 반 이름 A고 2학년 목요일 저녁반',
            kind: 'GRID',
            lessonDate: '2026-09-14',
            result: 'PARTIAL',
            completionRate: 55,
            resolvedByResubmission: true,
            dueAt: '2026-09-21T12:00:00Z',
            status: 'SUBMITTED',
            isLate: true,
            photoCount: 10,
          ),
        ],
      }),
      kids: const [(1, '김하늘'), (2, '아주긴이름의둘째아이')],
    );
    tester.view.physicalSize = const Size(360 * 3, 1600 * 3);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('parent-homework-1')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
