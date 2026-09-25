import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/features/parent/home/child_selector.dart';
import 'package:academy_app/features/parent/home/parent_home_controller.dart';
import 'package:academy_app/features/parent/home/parent_home_models.dart';
import 'package:academy_app/features/parent/home/parent_home_page.dart';
import 'package:academy_app/features/parent/home/parent_home_repository.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/shared/icons/icon_paths.dart';
import 'package:academy_app/shared/widgets/hero_field.dart';
import 'package:academy_app/shared/widgets/notice_card.dart';
import 'package:academy_app/shared/widgets/quick_rail.dart';
import 'package:academy_app/shared/widgets/section.dart';

class _Repo implements ParentHomeRepository {
  _Repo(this.result);
  Future<ParentHome> Function(int) result;
  final List<int> calls = [];
  @override
  Future<ParentHome> fetch(int id) {
    calls.add(id);
    return result(id);
  }
}

const _notice = NoticeItem(
  noticeId: 1,
  title: '추석 휴원 안내',
  pinned: false,
  hasAttachment: false,
  publishedAt: '2026-09-15T10:00:00+09:00',
);

const _exam = NextExam(
  examType: 'MIDTERM',
  startDate: '2026-10-01',
  scopeNote: null,
  dDay: 12,
);

const _clinic = NextClinic(
  clinicId: 7,
  clinicDate: '2026-09-20',
  arrivalTime: '17:00',
  dDay: 1,
);

ParentHome _home({
  String name = '김하늘',
  String? lessonDate = '2026-09-21',
  String? lessonTime = '19:00',
  int? dDay = 2,
  int pending = 2,
  NextExam? exam,
  NextClinic? clinic,
  AttendanceSummary attendance = const AttendanceSummary(
    present: 6,
    late: 1,
    absent: 1,
    sick: 0,
    excused: 0,
    makeup: 2,
  ),
}) => ParentHome(
  studentName: name,
  nextExam: exam,
  nextLessonDate: lessonDate,
  nextLessonTime: lessonTime,
  nextLessonDDay: dDay,
  notices: const HomeNotices(totalCount: 3, recent: [_notice]),
  pendingHomeworkCount: pending,
  nextClinic: clinic,
  thisMonthAttendance: attendance,
);

const _one = [Child(studentId: 1, name: '김하늘')];
const _two = [
  Child(studentId: 1, name: '김하늘'),
  Child(studentId: 2, name: '김바다'),
];

/// 진짜 [SelectedChild] 를 쓴다 — 화면이 목록을 부르고, 고른 아이로
/// 컨트롤러를 부르는 배선까지 여기서 검증한다. 대본이 떨어지면 던지므로
/// 넉넉히 깐다.
SelectedChild _selected(List<Child> kids) {
  final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
    ..httpClientAdapter = FakeAdapter(
      replies: List.filled(
        5,
        FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': [
              for (final k in kids) {'studentId': k.studentId, 'name': k.name},
            ],
          },
        ),
      ),
    );
  return SelectedChild(dio: dio, store: InMemoryKeyValueStore());
}

/// 실제 배치와 같게 `Scaffold` 안에 넣는다.
Widget _app(ParentHomeController c, SelectedChild? sc) => MaterialApp(
  home: Scaffold(
    body: ParentHomePage(controller: c, selectedChild: sc),
  ),
);

Finder _sectionTitle(String title) =>
    find.descendant(of: find.byType(SectionHead), matching: find.text(title));

SectionTone _homeworkTone(WidgetTester tester) => tester
    .widget<TintBlock>(
      find.descendant(
        of: find.byKey(const Key('homework-section')),
        matching: find.byType(TintBlock),
      ),
    )
    .tone;

void main() {
  /// 화면이 자녀 목록을 부르고 → 첫째를 고르고 → 컨트롤러를 부른다.
  /// **컨트롤러를 미리 load 하지 않는다** — 미리 불러 두면 화면이 부르지
  /// 않아도 그려져서 배선(과 didUpdateWidget)이 빠져도 모른다.
  Future<(ParentHomeController, SelectedChild, _Repo)> pump(
    WidgetTester tester,
    ParentHome home, {
    List<Child> kids = _one,
  }) async {
    final repo = _Repo((_) async => home);
    final c = ParentHomeController(repository: repo);
    final sc = _selected(kids);
    await tester.pumpWidget(_app(c, sc));
    await tester.pumpAndSettle();
    return (c, sc, repo);
  }

  testWidgets('인사말과 레일과 공지를 보여준다', (tester) async {
    await pump(tester, _home());
    expect(find.textContaining('김하늘'), findsWidgets);
    expect(find.byType(HeroField), findsOneWidget);
    expect(find.byType(QuickRail), findsOneWidget);
    expect(find.byType(NoticeCard), findsOneWidget);
  });

  testWidgets('selectedChild 가 null 이면 불러 둔 컨트롤러를 그린다', (tester) async {
    final c = ParentHomeController(repository: _Repo((_) async => _home()));
    await c.load(1);
    await tester.pumpWidget(_app(c, null));
    await tester.pumpAndSettle();
    expect(_sectionTitle('숙제'), findsOneWidget);
    expect(find.text('자녀 선택'), findsNothing);
  });

  testWidgets('인사말은 이름 한 곳만 주황이고 숙제 개수가 없다', (tester) async {
    // tailwind.config.js 의 accent 주석이 「홈 인사말의 이름」을 장식 세 곳
    // 중 하나로 못 박았다.
    await pump(tester, _home());
    final greeting = tester.widget<Text>(find.text('김하늘 학생\n학부모님, 환영합니다'));
    final spans = <InlineSpan>[];
    greeting.textSpan!.visitChildren((span) {
      spans.add(span);
      return true;
    });
    expect(spans.length, 2);
    expect((spans.first as TextSpan).text, '김하늘');
    expect((spans.first as TextSpan).style!.color, AppColors.accent300);
    expect((spans.last as TextSpan).style?.color, isNot(AppColors.accent300));
  });

  testWidgets('수업 시각이 없으면 날짜만 그린다', (tester) async {
    // 시각을 지어내 채우지 마라(반의 요일 슬롯이 없으면 null).
    await pump(tester, _home(lessonTime: null));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    final lesson = hero.stats.firstWhere((s) => s.label == '다음 수업');
    expect(lesson.sub, '09/21');
    expect(find.text('09/21'), findsOneWidget);
    expect(find.textContaining('19:00'), findsNothing);
    expect(find.textContaining('00:00'), findsNothing);
  });

  testWidgets('수업 시각이 있으면 날짜 옆에 붙는다', (tester) async {
    await pump(tester, _home());
    expect(find.text('09/21 19:00'), findsOneWidget);
  });

  testWidgets('수업이 없으면 그 칸을 만들지 않는다', (tester) async {
    await pump(tester, _home(lessonDate: null, lessonTime: null, dDay: null));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.any((s) => s.label == '다음 수업'), isFalse);
    expect(hero.stats.single.label, '미완료 숙제');
  });

  testWidgets('수업이 없고 클리닉만 있으면 클리닉이 그 칸의 주인이다', (tester) async {
    await pump(
      tester,
      _home(lessonDate: null, lessonTime: null, dDay: null, clinic: _clinic),
    );
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.first.label, '다음 클리닉');
    expect(hero.stats.first.value, 'D-1');
    expect(hero.stats.first.extra, isNull);
    expect(find.text('09/20 17:00 도착'), findsOneWidget);
  });

  testWidgets('클리닉은 수업 칸의 extra 로 묶인다', (tester) async {
    await pump(tester, _home(clinic: _clinic, exam: _exam));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.length, 3);
    expect(hero.stats.first.extra!.label, '다음 클리닉');
  });

  testWidgets('미완료 숙제가 0이면 주황을 끄고 「다 냈어요」다', (tester) async {
    await pump(tester, _home(pending: 0));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    final hw = hero.stats.firstWhere((s) => s.label == '미완료 숙제');
    expect(hw.value, '0');
    expect(hw.hot, isFalse);
    expect(hw.sub, '다 냈어요');
  });

  testWidgets('미완료 숙제가 있으면 주황이고 「확인 필요」다', (tester) async {
    // 학생 홈의 「확인하세요」가 아니다(웹 ParentHomePage.tsx).
    await pump(tester, _home(pending: 2));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.where((s) => s.hot).length, 1);
    final hw = hero.stats.firstWhere((s) => s.hot);
    expect(hw.label, '미완료 숙제');
    expect(hw.value, '2');
    expect(hw.sub, '확인 필요');
  });

  testWidgets('시험 칸은 종류 라벨·D-dDay·「10.01 시작」이고 오늘이면 웹처럼 D-0', (tester) async {
    await pump(
      tester,
      _home(
        exam: const NextExam(
          examType: 'MIDTERM',
          startDate: '2026-10-01',
          scopeNote: null,
          dDay: 0,
        ),
      ),
    );
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.last.label, '중간고사');
    expect(hero.stats.last.value, 'D-0');
    expect(hero.stats.last.sub, '10.01 시작');
    expect(find.text('MIDTERM'), findsNothing);
  });

  testWidgets('칸은 셋을 넘지 않는다', (tester) async {
    await pump(tester, _home(exam: _exam, clinic: _clinic));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.length, lessThanOrEqualTo(3));
  });

  testWidgets('레일은 여섯 칸이고 라벨·아이콘·경로가 웹 표와 짝이 맞다', (tester) async {
    await pump(tester, _home());
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    expect(rail.items.map((i) => (i.label, i.icon, i.route)).toList(), const [
      ('일정 · 출석', AppIconName.calendar, AppRoutes.parentSchedule),
      ('숙제', AppIconName.homework, AppRoutes.parentHomeworks),
      ('성적', AppIconName.chart, AppRoutes.parentScores),
      ('주간 레포트', AppIconName.book, AppRoutes.parentLessons),
      ('공지', AppIconName.megaphone, AppRoutes.parentNotices),
      ('내 정보', AppIconName.user, AppRoutes.parentMe),
    ]);
  });

  testWidgets('primary 는 숙제가 아니라 「일정 · 출석」 하나다', (tester) async {
    // 학생 홈과 뒤바뀌는 실수를 막는다.
    await pump(tester, _home());
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    final primaries = rail.items.where((i) => i.primary).toList();
    expect(primaries.length, 1);
    expect(primaries.single.label, '일정 · 출석');
    expect(primaries.single.route, AppRoutes.parentSchedule);
  });

  testWidgets('숙제·공지 칸에 점이 붙고 개수가 배선돼 있다', (tester) async {
    await pump(tester, _home(pending: 2));
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    expect(
      rail.items.firstWhere((i) => i.route == AppRoutes.parentHomeworks).count,
      2,
    );
    expect(
      rail.items.firstWhere((i) => i.route == AppRoutes.parentNotices).count,
      3,
    );
    expect(
      find.byKey(const Key('dot-${AppRoutes.parentHomeworks}')),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.byKey(const Key('rail-${AppRoutes.parentNotices}')),
      100,
      scrollable: find.descendant(
        of: find.byType(QuickRail),
        matching: find.byType(Scrollable),
      ),
    );
    expect(
      find.byKey(const Key('dot-${AppRoutes.parentNotices}')),
      findsOneWidget,
    );
  });

  testWidgets('구획 순서가 웹과 같다', (tester) async {
    // 숙제 · 시험 일정 · 이번 달 출석 · 학원 공지. 공지가 맨 아래다.
    await pump(tester, _home(exam: _exam));
    final ys = [
      for (final t in ['숙제', '시험 일정', '이번 달 출석', '학원 공지'])
        tester.getTopLeft(_sectionTitle(t)).dy,
    ];
    expect(ys[0], lessThan(ys[1]));
    expect(ys[1], lessThan(ys[2]));
    expect(ys[2], lessThan(ys[3]));
  });

  testWidgets('nextExam 이 null 이면 시험 일정 구획이 통째로 없다', (tester) async {
    await pump(tester, _home(exam: _exam));
    expect(_sectionTitle('시험 일정'), findsOneWidget);
    expect(find.text('시험 범위 미등록'), findsOneWidget);
    await pump(tester, _home());
    // 그려진 화면인지 먼저 본다 — 로딩 화면에서 「없다」는 무엇이든 통과한다.
    expect(_sectionTitle('숙제'), findsOneWidget);
    expect(_sectionTitle('시험 일정'), findsNothing);
    expect(find.text('시험 범위 미등록'), findsNothing);
  });

  testWidgets('컨트롤러와 자녀 선택을 바꿔 끼우면 새 것을 듣고 부른다', (tester) async {
    // didUpdateWidget 이 없으면 새 컨트롤러는 아무도 안 불러 영원히 로딩이다.
    await pump(tester, _home(name: '김하늘'));
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);
    final (_, _, repo) = await pump(tester, _home(name: '박하늘'));
    expect(repo.calls, [1]);
    expect(find.text('박하늘 학생\n학부모님, 환영합니다'), findsOneWidget);
  });

  testWidgets('숙제 구획의 색이 개수에 따라 바뀐다', (tester) async {
    // 웹 주석: 「안 낸 게 없으면 회색으로 내려앉는다 — 주황은 처리할 게
    // 있을 때만이다」.
    await pump(tester, _home(pending: 0));
    expect(_sectionTitle('숙제'), findsOneWidget);
    expect(_homeworkTone(tester), SectionTone.neutral);
    expect(find.text('미완료 숙제가 없습니다'), findsOneWidget);
    final head0 = tester.widget<SectionHead>(
      find.ancestor(
        of: _sectionTitle('숙제'),
        matching: find.byType(SectionHead),
      ),
    );
    expect(head0.tone, SectionTone.neutral);

    await pump(tester, _home(pending: 3));
    expect(_homeworkTone(tester), SectionTone.accent);
    expect(find.text('미완료 숙제가 3건 있습니다'), findsOneWidget);
    final head3 = tester.widget<SectionHead>(
      find.ancestor(
        of: _sectionTitle('숙제'),
        matching: find.byType(SectionHead),
      ),
    );
    expect(head3.tone, SectionTone.accent);
    expect(head3.count, 3);
  });

  testWidgets('출석 칸은 present + makeup 이다 (CLAUDE.md 5-1)', (tester) async {
    // MAKEUP(대체 등원)은 출석이다. 웹은 present 만 쓰지만 앱은 의도적으로
    // 다르다 — present 만 쓰면 8 이 나와 이 테스트가 깨진다.
    await pump(
      tester,
      _home(
        attendance: const AttendanceSummary(
          present: 8,
          late: 1,
          absent: 3,
          sick: 0,
          excused: 0,
          makeup: 2,
        ),
      ),
    );
    Finder cellValue(String key, String v) =>
        find.descendant(of: find.byKey(Key(key)), matching: find.text(v));
    expect(cellValue('attendance-present', '10'), findsOneWidget);
    expect(cellValue('attendance-late', '1'), findsOneWidget);
    expect(cellValue('attendance-absent', '3'), findsOneWidget);
  });

  testWidgets('출석 0을 숨기지 않는다', (tester) async {
    // 여기서 0은 「결석이 없다」는 좋은 소식이다. 칸이 사라지면 격자가 무너진다.
    await pump(
      tester,
      _home(
        attendance: const AttendanceSummary(
          present: 0,
          late: 0,
          absent: 0,
          sick: 0,
          excused: 0,
          makeup: 0,
        ),
      ),
    );
    for (final (key, label) in [
      ('attendance-present', '출석'),
      ('attendance-late', '지각'),
      ('attendance-absent', '결석'),
    ]) {
      final cell = find.byKey(Key(key));
      expect(cell, findsOneWidget, reason: '$label 칸이 없다');
      expect(
        find.descendant(of: cell, matching: find.text(label)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: cell, matching: find.text('0')),
        findsOneWidget,
      );
    }
  });

  testWidgets('자녀가 하나면 자녀 선택이 없고, 둘이면 있다', (tester) async {
    await pump(tester, _home());
    expect(_sectionTitle('숙제'), findsOneWidget);
    expect(find.text('자녀 선택'), findsNothing);

    await pump(tester, _home(), kids: _two);
    // 지면 안(HeroField 의 child)에 있다.
    expect(
      find.descendant(of: find.byType(HeroField), matching: find.text('자녀 선택')),
      findsOneWidget,
    );
  });

  testWidgets('자녀를 바꾸면 SelectedChild.select 를 거쳐 그 아이의 홈을 부른다', (tester) async {
    final repo = _Repo((id) async => _home(name: id == 1 ? '김하늘' : '김바다'));
    final c = ParentHomeController(repository: repo);
    final sc = _selected(_two);
    await tester.pumpWidget(_app(c, sc));
    await tester.pumpAndSettle();
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);

    await tester.tap(find.byKey(const Key('child-dropdown')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('김바다').last);
    await tester.pumpAndSettle();

    expect(sc.selectedStudentId, 2);
    expect(repo.calls.last, 2);
    expect(find.text('김바다 학생\n학부모님, 환영합니다'), findsOneWidget);
    // 드롭다운 글자 16 이상(CLAUDE.md 13-4).
    expect(ChildSelector.fontSize, greaterThanOrEqualTo(16));
  });

  testWidgets('홈은 요약이다 — 후기·공부 시간·랭킹이 없다', (tester) async {
    await pump(tester, _home(exam: _exam, clinic: _clinic));
    expect(_sectionTitle('숙제'), findsOneWidget);
    for (final banned in ['수강 후기', '후기', '공부 시간', '랭킹', '성적 그리드', '수업영상']) {
      expect(find.textContaining(banned), findsNothing, reason: banned);
    }
  });

  testWidgets('자녀 목록을 못 받으면 오류와 다시 시도를 보여준다', (tester) async {
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = FakeAdapter(
        replies: const [
          FakeReply(
            statusCode: 500,
            body: {
              'success': false,
              'data': null,
              'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
            },
          ),
          // 다시 시도에서 받을 성공 응답.
          FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': [
                {'studentId': 1, 'name': '김하늘'},
              ],
            },
          ),
        ],
      );
    final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
    final c = ParentHomeController(repository: _Repo((_) async => _home()));
    await tester.pumpWidget(_app(c, sc));
    await tester.pumpAndSettle();
    expect(find.text('다시 시도'), findsOneWidget);
    // 서버 문구 그대로다.
    expect(find.text('서버 오류입니다.'), findsOneWidget);

    // **누르면 목록을 다시 부르고 홈이 뜬다.** 버튼이 아무 일도 안 해도
    // 위 단언은 통과하므로 실제로 누른다.
    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);
    expect(find.text('다시 시도'), findsNothing);
  });

  testWidgets('자녀 목록 오류가 컨트롤러에 남은 홈보다 먼저다', (tester) async {
    // 목록을 못 받았으면 지금 사람의 자녀가 누군지 모른다 — 컨트롤러에
    // 남은 홈을 그리면 앞 사람 아이일 수 있다.
    final c = ParentHomeController(repository: _Repo((_) async => _home()));
    await c.load(1);
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = FakeAdapter(
        replies: const [
          FakeReply(
            statusCode: 500,
            body: {
              'success': false,
              'data': null,
              'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
            },
          ),
        ],
      );
    final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
    await tester.pumpWidget(_app(c, sc));
    await tester.pumpAndSettle();
    expect(find.text('서버 오류입니다.'), findsOneWidget);
    expect(find.textContaining('김하늘'), findsNothing);
  });

  testWidgets('홈을 못 받으면 오류와 다시 시도를 보여주고, 누르면 다시 부른다', (tester) async {
    var fail = true;
    final c = ParentHomeController(
      repository: _Repo((_) async {
        if (fail) throw Exception('x');
        return _home();
      }),
    );
    await tester.pumpWidget(_app(c, _selected(_one)));
    await tester.pumpAndSettle();
    expect(find.text('다시 시도'), findsOneWidget);
    expect(find.text('연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'), findsOneWidget);

    fail = false;
    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await pump(
      tester,
      _home(
        name: '남궁하늘바다',
        dDay: 61,
        exam: _exam,
        clinic: _clinic,
        pending: 12,
      ),
      kids: const [
        Child(studentId: 1, name: '남궁하늘바다'),
        Child(studentId: 2, name: '김바다'),
      ],
    );
    expect(_sectionTitle('이번 달 출석'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
