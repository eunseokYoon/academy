import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/icons/icon_paths.dart';

import 'package:academy_app/shared/lib/home_labels.dart';
import 'package:academy_app/shared/lib/homework_labels.dart';

import '../../../shared/widgets/app_bar_band.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/exam_schedule_section.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/hero_field.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/notice_card.dart';
import '../../../shared/widgets/quick_rail.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import 'last_lesson_card.dart';
import 'student_home_controller.dart';
import 'student_home_models.dart';

/// S-1 학생 홈. 웹 정본은 `frontend/src/routes/student/StudentHomePage.tsx` 다.
///
/// **미완료 숙제가 화면에서 가장 크다.** 학생이 서비스를 여는 이유가 「뭘 해야
/// 하는지」 확인이라, 이걸 아래로 내리면 화면의 목적이 사라진다. 지면의 주황
/// 칸과 아래 주황 구획이 같은 것을 두 번 말하는 건 **의도다** — 위는 개수,
/// 아래는 무엇인지다.
///
/// 구획 순서는 웹과 같다: 미완료 숙제 · 시험 일정 · 학원 공지 · 지난 수업.
/// 시험 범위가 시험 일정 구획 안에 있는 이유는 웹 주석에 있다 — 범위가 화면
/// 맨 아래 별도 카드에 있던 시절에는 D-61 을 보고 범위를 알려면 끝까지
/// 내려야 했다.
///
/// **앱바는 이 화면이 스크롤 맨 위에 직접 그린다**(셸은 안 그린다 — 웹의
/// `<AppBar>` 도 페이지와 함께 스크롤된다). 지면이 앱바(pb 32)를 파고들려면
/// **여기서 `-40` 을 적용해야 한다**(웹 `.field` 의 `-mt-10`).
/// `Transform.translate` 는 그리기만 옮기고 레이아웃은 안 옮기므로 **뒤따르는
/// 형제 전부가 같은 누적 오프셋(-80)을 받아야** 틈이 안 생긴다. 끝 여백은
/// [homeTailSpace] 가 탭 바 높이와 그 누적 오프셋으로 계산한다 — 고정값을
/// 넣지 마라.
class StudentHomePage extends StatefulWidget {
  const StudentHomePage({super.key, required this.controller});

  final StudentHomeController controller;

  @override
  State<StudentHomePage> createState() => _StudentHomePageState();
}

class _StudentHomePageState extends State<StudentHomePage>
    with ReappearReload<StudentHomePage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load();
  }

  /// 탭 재진입·앱 복귀([ReappearReload]). 60초 안이면 컨트롤러가 무시한다 —
  /// 여기서 조건을 다시 만들지 마라.
  @override
  void onReappear() => widget.controller.load();

  /// 컨트롤러가 바뀌면 **듣는 대상도 바꾼다.** 배선상 앱이 하나를 계속 들고
  /// 있지만, 안 갈아타면 새 컨트롤러는 아무도 안 듣는 채로 남아 화면이 옛
  /// 데이터(또는 영원한 로딩)에 묶인다.
  @override
  void didUpdateWidget(covariant StudentHomePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 레일·공지·구획 머리의 이동. **`push` 가 아니라 `go` 다** — 탭 전환과 같은
  /// 계열이라, 탭에 있는 목적지로 가는 칸은 그 탭으로 전환돼야 한다.
  void _go(String route) => context.go(route);

  void _goLessons() => _go(AppRoutes.studentLessons);

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final home = c.data;
    // 데이터가 없을 때만 전체 화면을 내준다. 새로고침 실패는 컨트롤러가
    // 기존 데이터를 지키므로 여기 오지 않는다.
    if (home == null) {
      if (c.status == HomeStatus.error) {
        return HomeStatusFrame(
          role: '학생',
          child: HomeErrorView(message: c.error, onRetry: c.refresh),
        );
      }
      return const HomeStatusFrame(role: '학생', child: FullScreenLoader());
    }

    final exam = home.nextExam;
    final lastLesson = home.lastLesson;

    return RefreshIndicator(
      onRefresh: c.refresh,
      child: ListView(
        padding: EdgeInsets.zero,
        // **겹치는 부분 전체가 Column 하나에 들어간다.** 이것을 ListView 의
        // 자식 넷으로 펴지 마라 — ListView 는 자식마다 RepaintBoundary·
        // IndexedSemantics 를 씌우는데, 그 프록시들은 자기 **레이아웃** 상자로
        // `size.contains` 를 먼저 보고 탈락시킨다. Transform 의 역변환 히트
        // 테스트는 그 뒤에 오므로, 위로 끌어올려 **그려진** 80px 은 손가락이
        // 닿지 않는다 — 레일 76px 중 아래 28px 만 눌리고 나머지는 죽는다.
        // Column 하나로 묶으면 프록시의 상자가 내용 전체를 덮고, Column 의
        // 히트 테스트가 자식(Transform)에게 그대로 넘겨 역변환이 살아난다.
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 앱바는 셸이 아니라 여기, 스크롤 안에 있다 — 웹처럼 같이
              // 올라가고, 아래 지면의 -40 이 이 앱바 위에 얹힌다. 셸로 빼면
              // 지면이 목록 뷰포트 밖으로 끌어올려져 날짜 줄이 잘린다.
              const AppBarBand(role: '학생'),
              // 지면이 앱바(pb 32)를 40 파고든다 — 웹 `.field` 의 -mt-10.
              Transform.translate(
                offset: const Offset(0, -40),
                child: HeroField(
                  eyebrow: todayLabel(),
                  title: _greeting(home),
                  stats: _stats(home),
                ),
              ),
              // 카드가 지면의 pb 64 안으로 40 더 올라온다(`.hero-lift`). 누적 -80.
              Transform.translate(
                offset: const Offset(0, -80),
                child: homeConstrain(
                  _card(
                    child: QuickRail(items: _railItems(home), onTap: _go),
                  ),
                ),
              ),
              // Transform 은 그리기만 옮기고 레이아웃은 안 옮긴다 —
              // 뒤따르는 형제 전부가 같은 누적 오프셋을 받아야 틈이 안 생긴다.
              Transform.translate(
                offset: const Offset(0, -80),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // 구획 사이 간격은 웹 `mt-5` = 20 이다.
                    const SizedBox(height: 20),
                    homeConstrain(_pendingSection(home)),
                    if (exam != null) ...[
                      const SizedBox(height: 20),
                      homeConstrain(
                        ExamScheduleSection(
                          examType: exam.examType,
                          startDate: exam.startDate,
                          scopeNote: exam.scopeNote,
                        ),
                      ),
                    ],
                    const SizedBox(height: 20),
                    homeConstrain(
                      NoticeCard(
                        totalCount: home.notices.totalCount,
                        recent: home.notices.recent
                            .map(
                              (n) => NoticeSummary(
                                id: n.noticeId,
                                title: n.title,
                                createdAt: shortDate(n.publishedAt),
                                // 빠뜨리면 고정 배지가 조용히 사라진다.
                                pinned: n.pinned,
                              ),
                            )
                            .toList(),
                        onTapAll: () => _go(AppRoutes.studentNotices),
                        // 읽음 표시가 없어 상세로 갈 이유가 없다 — 줄도 목록으로 간다.
                        onTapOne: (_) => _go(AppRoutes.studentNotices),
                      ),
                    ),
                    // 지난 수업이 통째로 비어 있으면 null 이다 — **머리까지 숨긴다.**
                    if (lastLesson != null) ...[
                      const SizedBox(height: 20),
                      homeConstrain(_lastLessonSection(lastLesson)),
                    ],
                  ],
                ),
              ),
              // 마지막 카드가 탭 바 윗변에서 [kHomeTailGap] 만큼 떨어지게.
              SizedBox(height: homeTailSpace(context)),
            ],
          ),
        ],
      ),
    );
  }

  /// 레일을 감싸는 흰 카드. **가로 패딩을 주지 마라** — 레일이 자기 여백을
  /// 갖고, 마지막 칸이 카드 끝에서 잘려야 「옆으로 넘길 수 있다」가 읽힌다
  /// (웹도 `p-4` 에 레일의 `-mx-4` 로 가로만 뚫는다).
  Widget _card({required Widget child}) =>
      AppCard(padding: const EdgeInsets.symmetric(vertical: 16), child: child);

  /// 순서·아이콘·라벨·경로가 웹 `StudentHomePage.tsx` 의 `QUICK_ITEMS` 그대로다.
  ///
  /// **숙제만 primary 다** — 학생이 이 화면에서 갈 곳이 하나면 거기다.
  /// 점 배지에 숫자는 쓰지 않는다([QuickItem.count] 가 0보다 크면 점만 붙는다).
  List<QuickItem> _railItems(StudentHome home) => [
    QuickItem(
      route: AppRoutes.studentHomeworks,
      icon: AppIconName.homework,
      label: '숙제',
      count: home.currentHomeworks.length,
      primary: true,
    ),
    const QuickItem(
      route: AppRoutes.studentLessons,
      icon: AppIconName.video,
      label: '수업',
    ),
    const QuickItem(
      route: AppRoutes.studentClinics,
      icon: AppIconName.clock,
      label: '스케줄',
    ),
    const QuickItem(
      route: AppRoutes.studentScores,
      icon: AppIconName.chart,
      label: '성적',
    ),
    const QuickItem(
      route: AppRoutes.studentOnlineTests,
      icon: AppIconName.test,
      label: '테스트',
    ),
    const QuickItem(
      route: AppRoutes.studentAttendances,
      icon: AppIconName.calendar,
      label: '출석',
    ),
    QuickItem(
      route: AppRoutes.studentNotices,
      icon: AppIconName.megaphone,
      label: '공지',
      count: home.notices.totalCount,
    ),
    const QuickItem(
      route: AppRoutes.studentQna,
      icon: AppIconName.question,
      label: '질문',
    ),
  ];

  /// 홈의 본론. **개수가 0이어도 구획은 남는다** — 자리가 사라지면 학생이
  /// 「숙제는 저기서 본다」를 잊는다.
  Widget _pendingSection(StudentHome home) {
    final homeworks = home.currentHomeworks;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        SectionHead(
          tone: SectionTone.accent,
          title: '미완료 숙제',
          count: homeworks.length,
          onTapAction: () => _go(AppRoutes.studentHomeworks),
        ),
        if (homeworks.isEmpty)
          const TintBlock(
            tone: SectionTone.neutral,
            children: [
              Padding(
                // 웹 `px-4 py-6`.
                padding: EdgeInsets.symmetric(horizontal: 16, vertical: 24),
                child: Text(
                  '미완료 숙제가 없습니다. 잘하고 있어요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 14, color: AppColors.slate500),
                ),
              ),
            ],
          )
        else
          TintBlock(
            tone: SectionTone.accent,
            children: [
              for (final homework in homeworks)
                _HomeworkRow(
                  homework: homework,
                  // 웹과 같이 그 숙제의 상세(S-3·S-4)로 간다. 숙제 탭으로
                  // 전환되고, 뒤로 가면 목록이다.
                  onTap: () => _go(
                    '${AppRoutes.studentHomeworks}/${homework.homeworkId}',
                  ),
                ),
            ],
          ),
      ],
    );
  }

  /// **카드만 있는 구획이 아니다.** 위에 회색 머리가 붙고, `lastLesson` 이
  /// null 이면 머리까지 통째로 사라진다.
  Widget _lastLessonSection(HomeLesson lesson) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      SectionHead(
        tone: SectionTone.neutral,
        title: '지난 수업',
        onTapAction: _goLessons,
      ),
      LastLessonCard(lesson: lesson, onTap: _goLessons),
    ],
  );
}

/// 미완료 숙제 한 줄.
///
/// **배지가 뒤집혀 있다.** 주황 면 위에서는 옅은 배지가 배경에 묻히므로 흰
/// 바탕에 주황 테두리로 뒤집어야 읽힌다. **마감이 지난 것도 빨강으로 올리지
/// 않는다** — 빨강은 이 앱에서 「결석·위험」만 뜻하고, 미완료 숙제는 위험이
/// 아니다.
class _HomeworkRow extends StatelessWidget {
  const _HomeworkRow({required this.homework, required this.onTap});

  final HomeHomework homework;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final dueAt = homework.dueAt;
    // InkWell 의 물결은 가장 가까운 Material 조상 위에 그려진다. TintBlock 은
    // Container 라, 감싸지 않으면 물결이 주황 틴트를 덮는다.
    return Material(
      key: const Key('homework-row'),
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        // 웹 `active:bg-accent-100/60`.
        highlightColor: AppColors.accent100.withValues(alpha: 0.6),
        child: Padding(
          // 웹 `px-3.5 py-3.5`.
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      homework.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        // 웹 `tracking-[-0.015em]` × 15px.
                        letterSpacing: -0.225,
                        color: AppColors.brand900,
                      ),
                    ),
                    // dueAt 은 서버가 늘 채워 보내지만(웹 타입도 non-null),
                    // 모델이 nullable 이라 없으면 줄을 만들지 않는다.
                    if (dueAt != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        '${formatDueAt(dueAt)} 마감',
                        style: TextStyle(
                          fontSize: 11.5,
                          color: AppColors.accent700.withValues(alpha: 0.75),
                          fontFeatures: kTabularFigures,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              // 웹 `gap-3`.
              const SizedBox(width: 12),
              Container(
                key: const Key('homework-remaining'),
                // 웹 `px-2 py-1`.
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(AppRadii.lg),
                  border: Border.all(color: AppColors.accent200),
                ),
                child: Text(
                  remainingLabel(homework.remainingMinutes),
                  style: const TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w700,
                    color: AppColors.accent600,
                    fontFeatures: kTabularFigures,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 인사말 두 줄. 이름 뒤는 **「학생,」** 이고(웹과 같다), 아랫줄이 오늘 해야
/// 할 일을 한 문장으로 말한다.
///
/// **이름 한 곳만 주황이다**(웹 `StudentHomePage.tsx` 의
/// `<span className="text-accent-300">`). `frontend/tailwind.config.js` 의
/// accent 주석이 이 색을 쓸 자리를 다섯으로 못 박았고, 장식으로 허용된 세 곳
/// 중 하나가 여기다 — 지우면 학생이 가장 자주 여는 화면에 브랜드색이 한 점도
/// 없다. **나머지 글자는 색을 주지 마라** — [HeroField] 의 기본색을 물려받는
/// 것이 맞고, 여기서 흰색을 다시 적으면 지면의 색 정본이 둘이 된다.
InlineSpan _greeting(StudentHome home) {
  final pending = home.currentHomeworks.length;
  final line = pending > 0 ? '미완료 숙제가 $pending개 있어요' : '미완료 숙제가 없어요';
  return TextSpan(
    children: [
      TextSpan(
        text: home.studentName,
        style: const TextStyle(color: AppColors.accent300),
      ),
      TextSpan(text: ' 학생,\n$line'),
    ],
  );
}

/// 지면의 숫자 칸. **값이 없는 항목은 뺀다** — null 을 「미정」으로 채우면
/// 없는 시험에 D-0 이 들어가 「시험이 오늘」로 읽힌다. 그래서 길이가
/// 1~3 으로 변한다.
///
/// 순서는 웹 `StudentHomePage.tsx` 와 같다 — 수업(+클리닉) · 미완료 숙제 ·
/// 시험. **`dDay` 는 서버 값이다.** 날짜로 다시 세지 마라.
List<HeroStat> _stats(StudentHome home) {
  final stats = <HeroStat>[];

  final lesson = home.nextLesson;
  final clinic = home.nextClinic;
  final clinicRow = clinic == null
      ? null
      : HeroStatExtra(
          label: '다음 클리닉',
          value: dDayLabel(clinic.dDay),
          sub:
              '${dateLabel(clinic.clinicDate, null)} '
              '${clinic.arrivalTime} 도착',
        );

  if (lesson != null) {
    stats.add(
      HeroStat(
        label: '다음 수업',
        value: dDayLabel(lesson.dDay),
        sub: dateLabel(lesson.lessonDate, lesson.startTime),
        // 같은 종류의 일정이라 한 칸에 묶는다. 칸을 넷으로 늘리면
        // 360px 에서 「D-61」이 줄바꿈된다.
        extra: clinicRow,
      ),
    );
  } else if (clinicRow != null) {
    // 수업이 없는데 클리닉만 있으면 **클리닉이 그 칸의 주인이 된다.**
    // extra 만 있는 칸을 만들면 첫 줄이 비어 칸이 깨진다.
    stats.add(
      HeroStat(
        label: clinicRow.label,
        value: clinicRow.value,
        sub: clinicRow.sub,
      ),
    );
  }

  // 미완료 숙제만 항상 있다. 0 도 뜻이 있는 값이다(「다 냈다」).
  // 대신 0 일 때는 주황을 끈다 — 처리할 게 없는데 주황이면 그 색이 뜻을 잃는다.
  final pending = home.currentHomeworks.length;
  stats.add(
    HeroStat(
      label: '미완료 숙제',
      value: '$pending',
      sub: pending > 0 ? '확인하세요' : '다 냈어요',
      hot: pending > 0,
    ),
  );

  final exam = home.nextExam;
  if (exam != null) {
    stats.add(
      HeroStat(
        // 라벨이 「시험」이 아니라 시험 종류다(웹과 같다).
        label: examLabel(exam.examType),
        // 수업·클리닉 칸과 같은 [dDayLabel] — 시험 당일은 「오늘」이다.
        value: dDayLabel(exam.dDay),
        // 연도를 뺀다. 360px 에서 「2026.10.12 시작」은 잘린다.
        sub: examStartShort(exam.startDate),
      ),
    );
  }

  return stats;
}
