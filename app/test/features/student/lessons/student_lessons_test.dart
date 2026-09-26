import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/features/student/lessons/student_lesson_data.dart';
import 'package:academy_app/features/student/lessons/student_lesson_detail_page.dart';
import 'package:academy_app/features/student/lessons/student_lessons_page.dart';
import 'package:academy_app/shared/lesson/lesson_report.dart';
import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/widgets/app_badge.dart';

StudentLessonListItem _row(int id, {bool isNew = false}) =>
    StudentLessonListItem(
      lessonId: id,
      lessonDate: '2026-09-0$id',
      title: id == 2 ? null : '수업 $id',
      classRoomName: 'A반',
      hasVideo: id == 1,
      isNew: isNew,
      homeworkTitle: id == 1 ? '단어 3과' : null,
    );

StudentLessonDetail _detail({
  List<LessonVideo> videos = const [],
  LessonHomework? homework,
  String? description,
  DayStatus? attendance,
}) => StudentLessonDetail(
  lessonId: 7,
  lessonDate: '2026-09-08',
  title: '관계대명사',
  classRoomName: 'A반',
  videos: videos,
  notes: const LessonNotes(
    content: '관계대명사 that',
    keyPoints: null,
    homeworkNote: '워크북 12쪽',
    clinicNote: null,
  ),
  homework: homework,
  homeworkDescription: description,
  attendance: attendance,
);

class _Repo implements StudentLessonRepository {
  _Repo({this.totalPages = 1, this.detailValue});

  final int totalPages;
  final StudentLessonDetail? detailValue;
  final List<LessonQuery> listCalls = [];

  @override
  Future<StudentLessonPage> list(LessonQuery q) async {
    listCalls.add(q);
    return StudentLessonPage(
      items: [_row(1, isNew: true), _row(2)],
      totalPages: totalPages,
    );
  }

  @override
  Future<StudentLessonDetail> detail(int lessonId) async => detailValue!;
}

/// 목록·상세가 `context.go` 로 옮기므로 라우터 안에 띄운다.
Future<GoRouter> _pumpRouter(WidgetTester tester, Widget page) async {
  tester.view.physicalSize = const Size(360 * 3, 1800 * 3);
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
        path: '/student/lessons',
        builder: (_, _) => const Text('목록 화면'),
        routes: [
          GoRoute(
            path: ':id',
            builder: (_, s) => Text('상세 ${s.pathParameters['id']}'),
          ),
        ],
      ),
      GoRoute(
        path: '/student/homeworks/:id',
        builder: (_, s) => Text('숙제 ${s.pathParameters['id']}'),
      ),
    ],
  );
  addTearDown(router.dispose);
  await tester.pumpWidget(MaterialApp.router(routerConfig: router));
  await tester.pumpAndSettle();
  return router;
}

void main() {
  test('달이 「전체 월」이면 month 를 보내지 않는다', () async {
    const page = FakeReply(
      statusCode: 200,
      body: {
        'success': true,
        'data': {'items': [], 'totalPages': 0},
      },
    );
    final adapter = FakeAdapter(replies: [page, page]);
    final repo = StudentLessonRepository(
      Dio(BaseOptions(baseUrl: 'https://x.test'))..httpClientAdapter = adapter,
    );
    await repo.list((year: 2026, month: null, page: 0));
    await repo.list((year: 2026, month: 9, page: 1));
    expect(adapter.received[0].queryParameters, {'year': 2026, 'page': 0});
    expect(adapter.received[1].queryParameters, {
      'year': 2026,
      'month': 9,
      'page': 1,
    });
  });

  group('목록', () {
    testWidgets('올해·전체 월로 부르고, 줄에 NEW·영상·숙제가 붙는다', (tester) async {
      final repo = _Repo();
      final c = StudentLessonsController(repository: repo);
      addTearDown(c.dispose);
      await _pumpRouter(
        tester,
        StudentLessonsPage(controller: c, thisYear: 2026),
      );
      expect(repo.listCalls, [(year: 2026, month: null, page: 0)]);
      expect(find.text('NEW'), findsOneWidget);
      expect(find.text('영상 있음'), findsOneWidget);
      expect(find.text('영상 없음'), findsOneWidget);
      expect(find.text('숙제 · 단어 3과'), findsOneWidget);
      expect(find.text('제목 없음'), findsOneWidget);
      // 한 페이지면 넘기기가 없다.
      expect(find.byKey(const Key('lessons-next')), findsNothing);
    });

    testWidgets('다음 페이지로 가고, 달을 바꾸면 첫 페이지로 돌아온다', (tester) async {
      final repo = _Repo(totalPages: 3);
      final c = StudentLessonsController(repository: repo);
      addTearDown(c.dispose);
      await _pumpRouter(
        tester,
        StudentLessonsPage(controller: c, thisYear: 2026),
      );
      await tester.tap(find.byKey(const Key('lessons-next')));
      await tester.pumpAndSettle();
      expect(repo.listCalls.last, (year: 2026, month: null, page: 1));
      expect(find.text('2 / 3'), findsOneWidget);

      await tester.tap(find.byKey(const Key('lessons-month')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('9월').last);
      await tester.pumpAndSettle();
      expect(repo.listCalls.last, (year: 2026, month: 9, page: 0));
    });

    testWidgets('줄을 누르면 그 수업의 상세로 간다', (tester) async {
      final c = StudentLessonsController(repository: _Repo());
      addTearDown(c.dispose);
      await _pumpRouter(
        tester,
        StudentLessonsPage(controller: c, thisYear: 2026),
      );
      await tester.tap(find.byKey(const ValueKey('lesson-2')));
      await tester.pumpAndSettle();
      expect(find.text('상세 2'), findsOneWidget);
    });
  });

  group('상세', () {
    Future<List<Uri>> pumpDetail(
      WidgetTester tester,
      StudentLessonDetail d, {
      bool opens = true,
    }) async {
      final opened = <Uri>[];
      final c = StudentLessonDetailController(
        repository: _Repo(detailValue: d),
      );
      addTearDown(c.dispose);
      await _pumpRouter(
        tester,
        StudentLessonDetailPage(
          lessonId: 7,
          controller: c,
          openUrl: (u) async {
            opened.add(u);
            return opens;
          },
        ),
      );
      return opened;
    }

    testWidgets('영상이 없으면 영상 영역이 없고, 출석 확정 전이면 배지가 없다', (tester) async {
      await pumpDetail(tester, _detail());
      expect(find.byKey(const Key('lesson-header')), findsOneWidget);
      expect(find.byKey(const Key('play-lesson')), findsNothing);
      expect(find.byType(AppBadge), findsNothing);
      expect(find.text('관계대명사 that'), findsOneWidget);
      expect(find.text('워크북 12쪽'), findsOneWidget);
      // 비어 있는 칸은 제목째 없다.
      expect(find.text('중점 사항'), findsNothing);
    });

    testWidgets('영상이 하나면 목록 없이 버튼 하나이고, 서버의 embedUrl 을 그대로 연다', (tester) async {
      final opened = await pumpDetail(
        tester,
        _detail(
          videos: const [
            LessonVideo(
              title: null,
              embedUrl: 'https://www.youtube.com/embed/abc',
            ),
          ],
          attendance: DayStatus.makeup,
        ),
      );
      expect(find.text('▶ 수업영상 시청하기'), findsOneWidget);
      expect(find.byKey(const ValueKey('video-0')), findsNothing);
      expect(find.text('대체 등원'), findsOneWidget);
      await tester.tap(find.byKey(const Key('play-lesson')));
      await tester.pumpAndSettle();
      expect(opened, [Uri.parse('https://www.youtube.com/embed/abc')]);
    });

    testWidgets('영상이 둘이면 번호 목록이 있고, 이름이 없으면 「영상 N」이다', (tester) async {
      final opened = await pumpDetail(
        tester,
        _detail(
          videos: const [
            LessonVideo(title: '1부', embedUrl: 'https://y/embed/a'),
            LessonVideo(title: null, embedUrl: 'https://y/embed/b?list=L'),
          ],
        ),
      );
      expect(find.text('▶ 수업영상 시청하기 (2개)'), findsOneWidget);
      expect(find.text('영상 2'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('video-1')));
      await tester.pumpAndSettle();
      expect(opened, [Uri.parse('https://y/embed/b?list=L')]);
    });

    testWidgets('영상을 못 열면 문구가 뜬다', (tester) async {
      await pumpDetail(
        tester,
        _detail(
          videos: const [LessonVideo(title: null, embedUrl: 'https://y/e')],
        ),
        opens: false,
      );
      await tester.tap(find.byKey(const Key('play-lesson')));
      await tester.pumpAndSettle();
      expect(find.text('영상을 열 수 없습니다. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
    });

    testWidgets('GRID ⭕ 숙제는 status 가 NOT_SUBMITTED 여도 「완료」다 (4-2)', (
      tester,
    ) async {
      await pumpDetail(
        tester,
        _detail(
          homework: const LessonHomework(
            homeworkId: 11,
            title: '단어 3과',
            kind: 'GRID',
            dueAt: null,
            result: 'DONE',
            completionRate: null,
            resolvedByResubmission: false,
            submissionStatus: 'NOT_SUBMITTED',
          ),
          description: '3과 전체',
        ),
      );
      expect(find.text('완료'), findsOneWidget);
      expect(find.text('미제출'), findsNothing);
      expect(find.text('3과 전체'), findsOneWidget);
      // 마감이 없으면 적지 않는다(1970년이 뜬다).
      expect(find.textContaining('마감'), findsNothing);

      await tester.tap(find.byKey(const Key('lesson-homework')));
      await tester.pumpAndSettle();
      expect(find.text('숙제 11'), findsOneWidget);
    });

    testWidgets('← 수업 목록 은 목록으로 간다', (tester) async {
      await pumpDetail(tester, _detail());
      await tester.tap(find.byKey(const Key('page-back')));
      await tester.pumpAndSettle();
      expect(find.text('목록 화면'), findsOneWidget);
    });
  });
}
