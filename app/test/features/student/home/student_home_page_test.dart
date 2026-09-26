import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/features/student/home/student_home_controller.dart';
import 'package:academy_app/features/student/home/student_home_models.dart';
import 'package:academy_app/features/student/home/student_home_page.dart';
import 'package:academy_app/features/student/home/student_home_repository.dart';
import 'package:academy_app/shared/icons/icon_paths.dart';
import 'package:academy_app/shared/widgets/hero_field.dart';
import 'package:academy_app/shared/widgets/notice_card.dart';
import 'package:academy_app/shared/widgets/quick_rail.dart';
import 'package:academy_app/shared/widgets/section.dart';

class _Repo implements StudentHomeRepository {
  _Repo(this.result);
  Future<StudentHome> Function() result;
  @override
  Future<StudentHome> fetch() => result();
}

const _notice = NoticeItem(
  noticeId: 1,
  title: '추석 휴원 안내',
  pinned: false,
  hasAttachment: false,
  publishedAt: '2026-09-15T10:00:00+09:00',
);

StudentHome _home({
  NextLesson? lesson,
  NextExam? exam,
  NextClinic? clinic,
  List<HomeHomework> homeworks = const [],
  HomeLesson? last,
  List<NoticeItem> notices = const [_notice],
}) => StudentHome(
  studentName: '김하늘',
  nextLesson: lesson,
  nextExam: exam,
  nextClinic: clinic,
  currentHomeworks: homeworks,
  lastLesson: last,
  notices: HomeNotices(totalCount: 3, recent: notices),
);

const _lesson = NextLesson(
  lessonDate: '2026-09-21',
  startTime: '19:00',
  dDay: 2,
  classRoomName: 'A반',
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
const _hw = HomeHomework(
  homeworkId: 11,
  title: '단어 3과',
  dueAt: '2026-09-21T21:00:00+09:00',
  status: 'NOT_SUBMITTED',
  remainingMinutes: 1500,
);
const _lastLesson = HomeLesson(
  lessonId: 3,
  lessonDate: '2026-09-14',
  title: '관계대명사',
  videoId: null,
  embedUrl: null,
  videoCount: 0,
  content: '관계대명사 that',
  homeworkNote: null,
);

/// 실제 배치와 같게 `Scaffold` 안에 넣는다 — 화면이 `RoleShell` 의 Scaffold
/// 안에 들어가고, 레일의 `InkWell` 이 Material 조상을 요구한다.
Widget _app(StudentHomeController c) => MaterialApp(
  home: Scaffold(body: StudentHomePage(controller: c)),
);

/// 구획 제목만 고른다. 「미완료 숙제」는 지면 칸의 라벨로도 나오므로
/// 그냥 `find.text` 로 세면 둘이 섞인다.
Finder _sectionTitle(String title) =>
    find.descendant(of: find.byType(SectionHead), matching: find.text(title));

void main() {
  Future<StudentHomeController> pump(
    WidgetTester tester,
    StudentHome home,
  ) async {
    final c = StudentHomeController(repository: _Repo(() async => home));
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();
    return c;
  }

  testWidgets('인사말과 레일과 공지를 보여준다', (tester) async {
    await pump(tester, _home(lesson: _lesson));
    expect(find.textContaining('김하늘'), findsWidgets);
    expect(find.byType(HeroField), findsOneWidget);
    expect(find.byType(QuickRail), findsOneWidget);
    expect(find.byType(NoticeCard), findsOneWidget);
  });

  testWidgets('인사말은 이름 한 곳만 주황이다', (tester) async {
    // 웹 tailwind.config.js 의 accent 주석이 이 색을 쓸 자리를 다섯으로 못
    // 박았고, 「장식으로 딱 세 곳」 중 하나가 「홈 인사말의 이름」이다. 빠지면
    // 학생이 가장 자주 여는 화면에 브랜드색이 한 점도 안 남는다.
    await pump(tester, _home(lesson: _lesson));
    final greeting = tester.widget<Text>(find.text('김하늘 학생,\n미완료 숙제가 없어요'));
    final spans = <InlineSpan>[];
    // textSpan 이 null 이면 지면이 평문 Text 로 되돌아간 것이다.
    greeting.textSpan!.visitChildren((span) {
      spans.add(span);
      return true;
    });
    expect(spans.length, 2);
    expect((spans.first as TextSpan).text, '김하늘');
    expect((spans.first as TextSpan).style!.color, AppColors.accent300);
    // 나머지 글자는 지면의 기본색을 물려받는다 — 주황이 번지면 안 된다.
    expect((spans.last as TextSpan).style?.color, isNot(AppColors.accent300));
  });

  testWidgets('레일은 여덟 칸이고 숙제만 primary 다', (tester) async {
    await pump(tester, _home(lesson: _lesson));
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    expect(rail.items.length, 8);
    expect(rail.items.where((i) => i.primary).length, 1);
    expect(rail.items.first.label, '숙제');
  });

  testWidgets('레일의 칸마다 라벨·아이콘·경로가 웹 표와 짝이 맞다', (tester) async {
    // 길이만 세면 「출석」 칸이 클리닉으로 가도 모른다. 웹 QUICK_ITEMS 의
    // 순서·짝 그대로다(브리프 정정 §7).
    await pump(tester, _home(lesson: _lesson));
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    expect(rail.items.map((i) => (i.label, i.icon, i.route)).toList(), const [
      ('숙제', AppIconName.homework, AppRoutes.studentHomeworks),
      ('수업', AppIconName.video, AppRoutes.studentLessons),
      ('스케줄', AppIconName.clock, AppRoutes.studentClinics),
      ('성적', AppIconName.chart, AppRoutes.studentScores),
      ('테스트', AppIconName.test, AppRoutes.studentOnlineTests),
      ('출석', AppIconName.calendar, AppRoutes.studentAttendances),
      ('공지', AppIconName.megaphone, AppRoutes.studentNotices),
      ('질문', AppIconName.question, AppRoutes.studentQna),
    ]);
  });

  testWidgets('숙제·공지 칸에 「볼 게 있다」 점이 붙는다', (tester) async {
    // 점이 칸 위의 유일한 「볼 게 있다」 신호다. count 배선이 빠지면 사라진다.
    await pump(tester, _home(lesson: _lesson, homeworks: [_hw]));
    expect(
      find.byKey(const Key('dot-${AppRoutes.studentHomeworks}')),
      findsOneWidget,
    );
    // 공지 칸은 레일 뒤쪽이라 지연 생성된다 — 굴려서 만든 뒤 본다.
    await tester.scrollUntilVisible(
      find.byKey(const Key('rail-${AppRoutes.studentNotices}')),
      100,
      scrollable: find.descendant(
        of: find.byType(QuickRail),
        matching: find.byType(Scrollable),
      ),
    );
    expect(
      find.byKey(const Key('dot-${AppRoutes.studentNotices}')),
      findsOneWidget,
    );
  });

  testWidgets('오늘 수업은 「오늘」, 오늘 시험은 웹처럼 「D-0」이다', (tester) async {
    // 수업은 dDayLabel, 시험은 `D-${dDay}` 를 그대로 쓴다(StudentHomePage.tsx
    // :112). 웹의 불일치이지만 웹이 정본이라 두 방향 모두 고정한다 —
    // 한쪽만 「정리」하면 여기서 깨진다.
    await pump(
      tester,
      _home(
        lesson: const NextLesson(
          lessonDate: '2026-09-21',
          startTime: '19:00',
          dDay: 0,
          classRoomName: 'A반',
        ),
        exam: const NextExam(
          examType: 'MIDTERM',
          startDate: '2026-09-21',
          scopeNote: null,
          dDay: 0,
        ),
      ),
    );
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.first.label, '다음 수업');
    expect(hero.stats.first.value, '오늘');
    expect(hero.stats.last.label, '중간고사');
    expect(hero.stats.last.value, 'D-0');
    expect(find.text('오늘'), findsOneWidget);
    expect(find.text('D-0'), findsOneWidget);
  });

  testWidgets('시험·클리닉 칸과 시험 구획의 글자가 웹과 같다', (tester) async {
    await pump(tester, _home(lesson: _lesson, clinic: _clinic, exam: _exam));
    // 지면 칸: 라벨은 시험 종류, sub 는 연도를 뺀 「10.01 시작」.
    // 원시 enum(MIDTERM)이 학생 화면에 나오면 안 된다.
    expect(find.text('MIDTERM'), findsNothing);
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.last.label, '중간고사');
    expect(find.text('10.01 시작'), findsOneWidget);
    // 클리닉 extra: 자기 dDay 와 「도착」 시각.
    expect(hero.stats.first.extra!.value, 'D-1');
    expect(find.text('09/20 17:00 도착'), findsOneWidget);
    expect(find.text('09/21 19:00'), findsOneWidget);
    // 시험 구획 첫 줄: 종류 + 연도를 남긴 「2026.10.01 시작」.
    final title = tester.widget<Text>(find.byKey(const Key('exam-title')));
    final spans = <InlineSpan>[];
    title.textSpan!.visitChildren((span) {
      spans.add(span);
      return true;
    });
    expect(spans.whereType<TextSpan>().map((t) => t.text).toList(), [
      '중간고사',
      '2026.10.01 시작',
    ]);
  });

  testWidgets('없는 값은 칸을 만들지 않는다', (tester) async {
    // nextExam 이 null 인데 D-0 을 채우면 시험이 오늘로 읽힌다.
    await pump(tester, _home());
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    // 숙제 칸은 항상 있다(0 도 뜻이 있다). 수업·시험이 없으니 하나뿐이다.
    expect(hero.stats.length, 1);
    expect(hero.stats.single.label, '미완료 숙제');
    expect(find.text('D-0'), findsNothing);
  });

  testWidgets('클리닉은 수업 칸의 extra 로 묶인다', (tester) async {
    // 같은 종류의 일정이라 한 칸이다. 칸을 넷으로 늘리면 360px 에서 깨진다.
    await pump(tester, _home(lesson: _lesson, clinic: _clinic, exam: _exam));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.length, 3); // 수업(+클리닉) · 숙제 · 시험
    expect(hero.stats.first.extra, isNotNull);
    expect(hero.stats.first.extra!.label, '다음 클리닉');
  });

  testWidgets('수업이 없고 클리닉만 있으면 클리닉이 그 칸의 주인이다', (tester) async {
    // 웹 StudentHomePage.tsx 의 `else if (clinicRow)` 다. extra 만 있는 칸을
    // 만들면 첫 줄이 비어 칸이 깨진다.
    await pump(tester, _home(clinic: _clinic));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.length, 2);
    expect(hero.stats.first.label, '다음 클리닉');
    expect(hero.stats.first.value, 'D-1');
    expect(hero.stats.first.extra, isNull);
  });

  testWidgets('칸은 절대 넷을 넘지 않는다', (tester) async {
    await pump(
      tester,
      _home(lesson: _lesson, clinic: _clinic, exam: _exam, homeworks: [_hw]),
    );
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.length, lessThanOrEqualTo(3));
  });

  testWidgets('미완료 숙제가 0이면 주황을 끈다', (tester) async {
    // 처리할 게 없는데 주황이면 그 색이 뜻을 잃는다.
    await pump(tester, _home(lesson: _lesson));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    final hw = hero.stats.firstWhere((s) => s.label == '미완료 숙제');
    expect(hw.value, '0');
    expect(hw.hot, isFalse);
    expect(hw.sub, '다 냈어요');
  });

  testWidgets('미완료 숙제가 있으면 주황이고 구획에 목록이 나온다', (tester) async {
    await pump(tester, _home(lesson: _lesson, homeworks: [_hw]));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    final hw = hero.stats.firstWhere((s) => s.label == '미완료 숙제');
    expect(hw.value, '1');
    expect(hw.hot, isTrue);
    expect(hw.sub, '확인하세요');
    // 위는 개수, 아래는 무엇인지 — 두 번 말하는 게 의도다.
    expect(find.text('단어 3과'), findsOneWidget);
  });

  testWidgets('주황은 화면에 하나뿐이다', (tester) async {
    await pump(tester, _home(lesson: _lesson, homeworks: [_hw]));
    final hero = tester.widget<HeroField>(find.byType(HeroField));
    expect(hero.stats.where((s) => s.hot).length, 1);
    final rail = tester.widget<QuickRail>(find.byType(QuickRail));
    expect(rail.items.where((i) => i.primary).length, 1);
  });

  testWidgets('구획 순서가 웹과 같다', (tester) async {
    // 미완료 숙제 · 시험 일정 · 학원 공지 · 지난 수업. 웹 StudentHomePage.tsx
    // 의 순서다 — 숙제가 맨 위인 것이 이 화면의 목적이다.
    await pump(
      tester,
      _home(lesson: _lesson, exam: _exam, homeworks: [_hw], last: _lastLesson),
    );
    final ys = [
      for (final t in ['미완료 숙제', '시험 일정', '학원 공지', '지난 수업'])
        tester.getTopLeft(_sectionTitle(t)).dy,
    ];
    expect(ys[0], lessThan(ys[1]));
    expect(ys[1], lessThan(ys[2]));
    expect(ys[2], lessThan(ys[3]));
  });

  testWidgets('nextExam 이 null 이면 시험 일정 구획이 통째로 없다', (tester) async {
    await pump(tester, _home(lesson: _lesson, exam: _exam));
    expect(_sectionTitle('시험 일정'), findsOneWidget);
    await pump(tester, _home(lesson: _lesson));
    // 먼저 **그려진 화면**인지 확인한다 — 두 번째 pump 뒤 로딩 화면에서
    // 「없다」를 단언하면 무엇을 지워도 통과한다(didUpdateWidget 회귀).
    expect(_sectionTitle('미완료 숙제'), findsOneWidget);
    expect(_sectionTitle('시험 일정'), findsNothing);
    expect(find.text('시험 범위 미등록'), findsNothing);
  });

  testWidgets('시험 범위는 있을 때와 없을 때 색이 다르다', (tester) async {
    // 없는 범위를 진한 글씨로 쓰면 선생님이 적은 것처럼 읽힌다.
    await pump(tester, _home(exam: _exam));
    final absent = tester.widget<Text>(find.text('시험 범위 미등록')).style!.color;

    await pump(
      tester,
      _home(
        exam: const NextExam(
          examType: 'MIDTERM',
          startDate: '2026-10-01',
          scopeNote: '교과서 3~5과',
          dDay: 12,
        ),
      ),
    );
    final present = tester.widget<Text>(find.text('교과서 3~5과')).style!.color;

    expect(absent, isNot(present));
  });

  testWidgets('lastLesson 이 null 이면 「지난 수업」 머리까지 없다', (tester) async {
    await pump(tester, _home(lesson: _lesson, last: _lastLesson));
    expect(_sectionTitle('지난 수업'), findsOneWidget);
    await pump(tester, _home(lesson: _lesson));
    // 그려진 화면이어야 「없다」가 뜻을 갖는다(위 테스트와 같은 이유).
    expect(_sectionTitle('학원 공지'), findsOneWidget);
    expect(find.text('지난 수업'), findsNothing);
  });

  testWidgets('미완료 숙제가 0이면 안내 한 줄만 있고 줄이 없다', (tester) async {
    await pump(tester, _home(lesson: _lesson));
    expect(_sectionTitle('미완료 숙제'), findsOneWidget);
    expect(find.text('미완료 숙제가 없습니다. 잘하고 있어요.'), findsOneWidget);
    expect(find.byKey(const Key('homework-row')), findsNothing);
  });

  testWidgets('줄의 배지는 흰 바탕 + accent600 글자다', (tester) async {
    // 주황 면 위에서는 옅은 배지가 배경에 묻힌다. 흰 바탕에 주황 테두리로
    // 뒤집어야 읽힌다.
    await pump(tester, _home(lesson: _lesson, homeworks: [_hw]));
    final badge = find.byKey(const Key('homework-remaining'));
    final box = tester.widget<Container>(badge).decoration as BoxDecoration;
    expect(box.color, Colors.white);
    expect((box.border as Border).top.color, AppColors.accent200);
    final label = tester.widget<Text>(
      find.descendant(of: badge, matching: find.byType(Text)),
    );
    expect(label.style!.color, AppColors.accent600);
  });

  testWidgets('마감이 지나도 빨강이 아니다', (tester) async {
    // 빨강은 이 앱에서 「결석·위험」만 뜻한다. 미완료 숙제는 위험이 아니다.
    await pump(
      tester,
      _home(
        lesson: _lesson,
        homeworks: const [
          HomeHomework(
            homeworkId: 12,
            title: '리뷰 테스트 오답',
            dueAt: '2026-09-18T21:00:00+09:00',
            status: 'NOT_SUBMITTED',
            remainingMinutes: -2880,
          ),
        ],
      ),
    );
    final label = tester.widget<Text>(
      find.descendant(
        of: find.byKey(const Key('homework-remaining')),
        matching: find.byType(Text),
      ),
    );
    expect(label.style!.color, AppColors.accent600);

    // const Set 으로 두면 Color 에 primitive equality 가 없어 컴파일이 막는다.
    final reds = <Color>[AppColors.red50, AppColors.red200, AppColors.red700];
    for (final t in tester.widgetList<Text>(find.byType(Text))) {
      expect(reds.contains(t.style?.color), isFalse, reason: '${t.data} 가 빨갛다');
    }
    for (final c in tester.widgetList<Container>(find.byType(Container))) {
      final d = c.decoration;
      if (d is BoxDecoration) expect(reds.contains(d.color), isFalse);
    }
  });

  testWidgets('남은 시간 경계가 웹 함수와 같다', (tester) async {
    // 웹 shared/homework/types.ts 의 remainingLabel 이 정본이다.
    // 경계는 60 과 60*24 이고 Math.floor = 정수 나눗셈이다.
    const cases = <(int, String)>[
      (59, '59분 남음'),
      (60, '1시간 남음'),
      (1439, '23시간 남음'),
      (1440, '1일 남음'),
      (-1, '마감 1분 지남'),
      (-59, '마감 59분 지남'),
      (-60, '마감 1시간 지남'),
      (-1440, '마감 1일 지남'),
    ];
    await pump(
      tester,
      _home(
        homeworks: [
          for (var i = 0; i < cases.length; i++)
            HomeHomework(
              homeworkId: i,
              title: '숙제 $i',
              dueAt: '2026-09-21T21:00:00+09:00',
              status: 'NOT_SUBMITTED',
              remainingMinutes: cases[i].$1,
            ),
        ],
      ),
    );
    for (final c in cases) {
      expect(find.text(c.$2), findsOneWidget, reason: c.$2);
    }
  });

  testWidgets('마감 시각이 9시간 어긋나지 않는다', (tester) async {
    // DateTime.parse 는 +09:00 을 UTC 로 돌려준다. 기기 시간대(.toLocal())
    // 로 읽으면 UTC 러너·해외 기기에서 21:00 마감이 12:00 으로 조용히
    // 바뀐다. `TZ=UTC flutter test` 로도 통과해야 한다.
    await pump(tester, _home(homeworks: [_hw]));
    expect(find.text('9월 21일 21:00 마감'), findsOneWidget);
  });

  testWidgets('UTC 로 온 마감도 한국 시각으로 그린다(날짜가 넘어가는 경우)', (tester) async {
    await pump(
      tester,
      _home(
        homeworks: [
          const HomeHomework(
            homeworkId: 1,
            title: '단어 3과',
            dueAt: '2026-09-21T15:30:00Z',
            status: 'NOT_SUBMITTED',
            remainingMinutes: 1500,
          ),
        ],
      ),
    );
    expect(find.text('9월 22일 00:30 마감'), findsOneWidget);
  });

  testWidgets('공지 줄의 고정 배지가 나온다', (tester) async {
    await pump(
      tester,
      _home(
        lesson: _lesson,
        notices: const [
          NoticeItem(
            noticeId: 2,
            title: '중간고사 대비 특강',
            pinned: true,
            hasAttachment: false,
            publishedAt: '2026-09-15T10:00:00+09:00',
          ),
        ],
      ),
    );
    expect(find.byKey(const Key('notice-pinned')), findsOneWidget);
    expect(find.text('09/15'), findsOneWidget);
  });

  testWidgets('실패하면 오류와 다시 시도를 보여주고, 누르면 다시 부른다', (tester) async {
    var fail = true;
    final c = StudentHomeController(
      repository: _Repo(() async {
        if (fail) throw Exception('x');
        return _home(lesson: _lesson);
      }),
    );
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();
    expect(find.text('다시 시도'), findsOneWidget);
    expect(find.text('연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'), findsOneWidget);

    // 버튼이 아무 일도 안 해도 위 단언은 통과한다 — 실제로 누른다.
    fail = false;
    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.text('김하늘 학생,\n미완료 숙제가 없어요'), findsOneWidget);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await pump(
      tester,
      _home(
        lesson: const NextLesson(
          lessonDate: '2026-11-20',
          startTime: '19:00',
          dDay: 61,
          classRoomName: 'A고 2학년 목요일반',
        ),
        clinic: _clinic,
        exam: const NextExam(
          examType: 'MIDTERM',
          startDate: '2026-10-01',
          scopeNote: '교과서 3~5과 본문 전체와 부교재 Unit 7~9',
          dDay: 12,
        ),
        homeworks: const [
          HomeHomework(
            homeworkId: 11,
            title: '부교재 Unit 7 본문 해석과 단어 시험 대비 3회독',
            dueAt: '2026-09-21T21:00:00+09:00',
            status: 'NOT_SUBMITTED',
            remainingMinutes: 1500,
          ),
        ],
        last: _lastLesson,
      ),
    );
    expect(tester.takeException(), isNull);
  });
}
