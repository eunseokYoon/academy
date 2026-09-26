import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/icons/icon_paths.dart';

import 'package:academy_app/shared/lib/home_labels.dart';

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
import '../../student/home/student_home_controller.dart' show HomeStatus;
import '../selected_child.dart';
import 'child_selector.dart';
import 'parent_home_controller.dart';
import 'parent_home_models.dart';

/// P-1 학부모 홈. 웹 정본은 `frontend/src/routes/parent/ParentHomePage.tsx` 다.
///
/// **홈은 요약이다.** 수업 제목·내용, 숙제 사진·피드백, 성적을 넣지 마라.
/// 학생 홈(S-1)과 같은 언어를 쓴다 — 지면 + 숫자 칸 + 색 구획. 두 화면이
/// 어긋나면 「아이 폰에는 다르게 나온다」는 문의가 된다.
///
/// 구획 순서는 웹과 같다: 숙제 · 시험 일정 · 이번 달 출석 · 학원 공지.
///
/// **자녀 목록의 주인은 [SelectedChild] 다.** 이 화면은 목록을 부르고
/// (학부모 영역의 입구가 여기다 — 로그인 직후 라우터가 `/parent` 로 보낸다),
/// 선택이 바뀔 때마다 [ParentHomeController.load] 를 부른다. **여기서 홈을
/// 직접 fetch 하지 마라** — 자녀를 빠르게 바꿀 때의 경합은 컨트롤러가 막는다.
///
/// 지면 겹침 구조는 학생 홈과 같다(스크롤 안의 앱바, `-40`, `-80`, `-80`,
/// 끝 [homeTailSpace]).
/// 이유는 `student_home_page.dart` 의 클래스 주석에 있다.
class ParentHomePage extends StatefulWidget {
  const ParentHomePage({
    super.key,
    required this.controller,
    required this.selectedChild,
  });

  final ParentHomeController controller;

  /// null 이면 자녀 선택 UI 를 그리지 않고 목록도 부르지 않는다 —
  /// 컨트롤러가 이미 불러 둔 것을 그대로 보여준다(화면 테스트용).
  final SelectedChild? selectedChild;

  @override
  State<ParentHomePage> createState() => _ParentHomePageState();
}

class _ParentHomePageState extends State<ParentHomePage>
    with ReappearReload<ParentHomePage> {
  /// 자녀 목록을 못 받았을 때의 서버 문구. 목록이 없으면 홈을 부를 id 가
  /// 없으므로 컨트롤러의 오류와 별개로 들고 있어야 한다.
  String? _childrenError;

  @override
  void initState() {
    super.initState();
    _attach(widget.controller, widget.selectedChild);
  }

  /// 컨트롤러·자녀 선택이 바뀌면 **듣는 대상도 바꾼다.** 안 갈아타면 새
  /// 컨트롤러는 아무도 안 듣고 아무도 안 부르는 채로 남아 화면이 영원한
  /// 로딩에 묶인다(학생 홈과 같은 회귀).
  @override
  void didUpdateWidget(covariant ParentHomePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.selectedChild != widget.selectedChild) {
      _detach(oldWidget.controller, oldWidget.selectedChild);
      _childrenError = null;
      _attach(widget.controller, widget.selectedChild);
    }
  }

  @override
  void dispose() {
    _detach(widget.controller, widget.selectedChild);
    super.dispose();
  }

  void _attach(ParentHomeController c, SelectedChild? sc) {
    c.addListener(_onChanged);
    if (sc == null) return;
    sc.addListener(_onSelection);
    // 이미 골라져 있으면(컨트롤러가 바뀐 경우 등) 바로 부른다. 60초 규칙은
    // 컨트롤러가 지킨다 — 여기서 조건을 다시 만들지 마라.
    final id = sc.selectedStudentId;
    if (id != null) c.load(id);
    if (!sc.loading) _loadChildren(sc);
  }

  /// 탭 재진입·앱 복귀([ReappearReload]). 지금 고른 자녀를 다시 부른다 —
  /// 60초 안이면 컨트롤러가 무시한다. 여기서 조건을 다시 만들지 마라.
  @override
  void onReappear() {
    final id = widget.selectedChild?.selectedStudentId;
    if (id != null) widget.controller.load(id);
  }

  void _detach(ParentHomeController c, SelectedChild? sc) {
    c.removeListener(_onChanged);
    sc?.removeListener(_onSelection);
  }

  /// 학부모 영역에 들어올 때 한 번. 로그아웃하면 셸째로 사라지므로 다른
  /// 학부모가 로그인하면 새로 부른다 — 앞 사람의 자녀 목록이 남지 않는다.
  Future<void> _loadChildren(SelectedChild sc) async {
    try {
      await sc.load();
    } on ApiException catch (e) {
      // 서버 문구 그대로다. 감싸지 마라.
      if (mounted) setState(() => _childrenError = e.message);
    } catch (_) {
      if (mounted) {
        setState(() => _childrenError = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      }
    }
  }

  Future<void> _retryChildren() async {
    final sc = widget.selectedChild;
    if (sc == null) return;
    setState(() => _childrenError = null);
    await _loadChildren(sc);
  }

  /// 자녀가 바뀌면 그 아이의 홈을 부른다. 같은 아이면 컨트롤러가 무시한다.
  void _onSelection() {
    final id = widget.selectedChild?.selectedStudentId;
    if (id != null) widget.controller.load(id);
    _onChanged();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// **`push` 가 아니라 `go` 다** — 탭에 있는 목적지로 가는 칸은 그 탭으로
  /// 전환돼야 한다(학생 홈과 같다).
  void _go(String route) => context.go(route);

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final sc = widget.selectedChild;
    // **자녀 목록 오류가 먼저다.** 목록을 못 받았으면 지금 로그인한
    // 사람의 자녀가 누군지 모르는 것이다 — 컨트롤러에 무엇이 남아 있든
    // 그것을 그리지 않는다.
    final childrenError = _childrenError;
    if (childrenError != null) {
      return HomeStatusFrame(
        role: '학부모',
        child: HomeErrorView(message: childrenError, onRetry: _retryChildren),
      );
    }
    final home = c.data;
    if (home == null) {
      if (c.status == HomeStatus.error) {
        return HomeStatusFrame(
          role: '학부모',
          child: HomeErrorView(message: c.error, onRetry: c.refresh),
        );
      }
      // 웹도 자녀가 정해지기 전에는 「불러오는 중」이다.
      return const HomeStatusFrame(role: '학부모', child: FullScreenLoader());
    }

    final exam = home.nextExam;
    final pending = home.pendingHomeworkCount;

    return RefreshIndicator(
      onRefresh: c.refresh,
      child: ListView(
        padding: EdgeInsets.zero,
        // **겹치는 부분 전체가 Column 하나에 들어간다.** ListView 의 자식으로
        // 펴지 마라 — ListView 가 자식마다 씌우는 RepaintBoundary 가 자기
        // 레이아웃 상자로 히트 테스트를 먼저 탈락시켜, 위로 끌어올려 그려진
        // 레일 대부분이 눌리지 않는다. 자세한 이유는 student_home_page.dart
        // 에 있고, smoke_test 의 「레일 여섯 칸」이 잠근다.
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 앱바는 셸이 아니라 여기, 스크롤 안에 있다 — 웹처럼 같이
              // 올라가고, 아래 지면의 -40 이 이 앱바 위에 얹힌다. 셸로 빼면
              // 지면이 목록 뷰포트 밖으로 끌어올려져 날짜 줄이 잘린다.
              const AppBarBand(role: '학부모'),
              // 지면이 앱바(pb 32)를 40 파고든다 — 웹 `.field` 의 -mt-10.
              Transform.translate(
                offset: const Offset(0, -40),
                child: HeroField(
                  eyebrow: todayLabel(),
                  title: _greeting(home),
                  stats: _stats(home),
                  child: sc == null
                      ? null
                      : ChildSelector(
                          children: sc.children,
                          selectedStudentId: sc.selectedStudentId,
                          onSelect: sc.select,
                        ),
                ),
              ),
              // 카드가 지면의 pb 64 안으로 40 더 올라온다(`.hero-lift`). 누적 -80.
              Transform.translate(
                offset: const Offset(0, -80),
                child: homeConstrain(
                  AppCard(
                    // 가로 패딩을 주지 마라 — 레일이 자기 여백을 갖는다.
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: QuickRail(items: _railItems(home), onTap: _go),
                  ),
                ),
              ),
              // Transform 은 그리기만 옮긴다 — 뒤따르는 형제 전부가 같은 누적
              // 오프셋을 받아야 틈이 안 생긴다.
              Transform.translate(
                offset: const Offset(0, -80),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // 구획 사이 간격은 웹 `mt-5` = 20 이다.
                    const SizedBox(height: 20),
                    homeConstrain(_homeworkSection(pending)),
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
                    homeConstrain(_attendanceSection(home.thisMonthAttendance)),
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
                                pinned: n.pinned,
                              ),
                            )
                            .toList(),
                        onTapAll: () => _go(AppRoutes.parentNotices),
                        // 읽음 표시가 없어 상세로 갈 이유가 없다 — 목록으로 간다.
                        onTapOne: (_) => _go(AppRoutes.parentNotices),
                      ),
                    ),
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

  /// 순서·아이콘·라벨·경로가 웹 `ParentHomePage.tsx` 의 `QUICK_ITEMS` 그대로다.
  ///
  /// **primary 는 숙제가 아니라 「일정 · 출석」이다** — 학생 홈과 반대다.
  /// 학부모가 이 화면에서 갈 곳이 하나면 거기다.
  ///
  /// 메뉴는 여섯이다(웹 주석). 수업영상은 학부모 화면에서 제외됐고, 수강
  /// 후기는 학생 전용이다. **KW-Study(공부 시간·랭킹) 메뉴도 넣지 마라.**
  List<QuickItem> _railItems(ParentHome home) => [
    const QuickItem(
      route: AppRoutes.parentSchedule,
      icon: AppIconName.calendar,
      label: '일정 · 출석',
      primary: true,
    ),
    QuickItem(
      route: AppRoutes.parentHomeworks,
      icon: AppIconName.homework,
      label: '숙제',
      count: home.pendingHomeworkCount,
    ),
    const QuickItem(
      route: AppRoutes.parentScores,
      icon: AppIconName.chart,
      label: '성적',
    ),
    const QuickItem(
      route: AppRoutes.parentLessons,
      icon: AppIconName.book,
      label: '주간 레포트',
    ),
    QuickItem(
      route: AppRoutes.parentNotices,
      icon: AppIconName.megaphone,
      label: '공지',
      count: home.notices.totalCount,
    ),
    const QuickItem(
      route: AppRoutes.parentMe,
      icon: AppIconName.user,
      label: '내 정보',
    ),
  ];

  /// 학부모 화면의 본론. 학생 홈과 달리 목록이 아니라 **개수 하나라 한 줄**
  /// 로 끝난다. **안 낸 게 없으면 회색으로 내려앉는다** — 주황은 처리할 게
  /// 있을 때만이다(웹 주석). 학생 홈의 「0이면 hot 을 끈다」와 같은 규칙이다.
  Widget _homeworkSection(int pending) {
    final tone = pending > 0 ? SectionTone.accent : SectionTone.neutral;
    return Column(
      key: const Key('homework-section'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        SectionHead(
          tone: tone,
          title: '숙제',
          count: pending,
          onTapAction: () => _go(AppRoutes.parentHomeworks),
        ),
        TintBlock(
          tone: tone,
          children: [
            Padding(
              // 웹 `px-3.5 py-3.5`.
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              child: Text(
                pending > 0 ? '미완료 숙제가 $pending건 있습니다' : '미완료 숙제가 없습니다',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: AppColors.brand900,
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _attendanceSection(AttendanceSummary a) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      SectionHead(
        tone: SectionTone.brand,
        title: '이번 달 출석',
        onTapAction: () => _go(AppRoutes.parentSchedule),
      ),
      TintBlock(
        tone: SectionTone.brand,
        children: [
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // **웹과 다르다 — 의도다.** 웹은 `present` 만 쓰는데 백엔드의
                // `present` 는 MAKEUP(대체 등원)을 포함하지 않는다
                // (`AttendanceSummaryResponse` 의 switch). CLAUDE.md 5-1:
                // 「MAKEUP 은 출석이다 … 결석 쪽으로 세지 마라 — 학부모
                // 캘린더 요약의 「출석」 칸이 present + makeup」. `present` 로
                // 「웹에 맞춰」 되돌리면 대체 등원한 날이 세 칸 어디에도 안
                // 잡힌다. 웹을 고치는 것은 별도 작업이다.
                _AttendanceCell(
                  key: const Key('attendance-present'),
                  label: '출석',
                  value: a.attended,
                ),
                const _CellDivider(),
                _AttendanceCell(
                  key: const Key('attendance-late'),
                  label: '지각',
                  value: a.late,
                ),
                const _CellDivider(),
                _AttendanceCell(
                  key: const Key('attendance-absent'),
                  label: '결석',
                  value: a.absent,
                ),
              ],
            ),
          ),
        ],
      ),
    ],
  );
}

/// 출석·지각·결석 한 칸. **0을 숨기지 않는다**(웹 주석) — 여기서 0은
/// 「값이 없다」가 아니라 「결석이 없다」는 좋은 소식이고, 칸이 사라지면
/// 셋이 나란히 서던 격자가 무너진다. 지면 숫자 칸이 값 없는 항목을 빼는
/// 것과는 **반대 방향**이다.
class _AttendanceCell extends StatelessWidget {
  const _AttendanceCell({super.key, required this.label, required this.value});

  final String label;
  final int value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Padding(
        // 웹 `px-3 py-3`.
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              label,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: AppColors.brand600.withValues(alpha: 0.7),
              ),
            ),
            // 웹 `mt-1`.
            const SizedBox(height: 4),
            Text(
              '$value',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w800,
                // 웹 `tracking-[-0.03em]` × 20px.
                letterSpacing: -0.6,
                color: AppColors.brand900,
                fontFeatures: kTabularFigures,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 웹 `divide-x divide-brand-100`.
class _CellDivider extends StatelessWidget {
  const _CellDivider();

  @override
  Widget build(BuildContext context) =>
      const SizedBox(width: 1, child: ColoredBox(color: AppColors.brand100));
}

/// 인사말 두 줄: 「{이름} 학생」 / 「학부모님, 환영합니다」.
///
/// **이름 한 곳만 `accent300` 이다**(웹 `<span className="text-accent-300">`).
/// `frontend/tailwind.config.js` 의 accent 주석이 장식으로 허용한 세 곳 중
/// 하나가 「홈 인사말의 이름」이다. 나머지 글자는 색을 주지 마라 —
/// [HeroField] 의 기본색을 물려받는다.
///
/// 학생 홈과 달리 **숙제 개수가 인사말에 안 들어간다**(웹과 같다).
InlineSpan _greeting(ParentHome home) => TextSpan(
  children: [
    TextSpan(
      text: home.studentName,
      style: const TextStyle(color: AppColors.accent300),
    ),
    const TextSpan(text: ' 학생\n학부모님, 환영합니다'),
  ],
);

/// 지면의 숫자 칸 1~3개. **값이 없는 항목은 뺀다.** 순서는 웹과 같다 —
/// 수업(+클리닉) · 미완료 숙제 · 시험. **dDay 는 서버 값이다.**
List<HeroStat> _stats(ParentHome home) {
  final stats = <HeroStat>[];

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

  final lessonDate = home.nextLessonDate;
  final lessonDDay = home.nextLessonDDay;
  if (lessonDate != null && lessonDDay != null) {
    stats.add(
      HeroStat(
        label: '다음 수업',
        value: dDayLabel(lessonDDay),
        // **nextLessonTime 은 null 일 수 있다**(반의 요일 슬롯이 없으면).
        // 그때는 날짜만이다 — 시각을 지어내지 마라.
        sub: dateLabel(lessonDate, home.nextLessonTime),
        extra: clinicRow,
      ),
    );
  } else if (clinicRow != null) {
    // 수업이 없고 클리닉만 있으면 클리닉이 그 칸의 주인이 된다.
    stats.add(
      HeroStat(
        label: clinicRow.label,
        value: clinicRow.value,
        sub: clinicRow.sub,
      ),
    );
  }

  // 항상 있다. 0 도 뜻이 있는 값이다. 0 이면 주황을 끈다.
  // sub 가 학생 홈의 「확인하세요」가 아니라 「확인 필요」다(웹과 같다).
  final pending = home.pendingHomeworkCount;
  stats.add(
    HeroStat(
      label: '미완료 숙제',
      value: '$pending',
      sub: pending > 0 ? '확인 필요' : '다 냈어요',
      hot: pending > 0,
    ),
  );

  final exam = home.nextExam;
  if (exam != null) {
    stats.add(
      HeroStat(
        label: examLabel(exam.examType),
        // 웹 그대로 `D-${dDay}` — 오늘이면 「D-0」이다(학생 홈과 같은 불일치,
        // 웹이 정본이라 그대로 둔다).
        value: 'D-${exam.dDay}',
        sub: examStartShort(exam.startDate),
      ),
    );
  }

  return stats;
}
