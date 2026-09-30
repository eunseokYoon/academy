import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/home_labels.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/lesson/lesson_report.dart';
import '../../../shared/score/correct_count_chart.dart';
import '../../../shared/score/score_data.dart';
import '../../../shared/score/score_value.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/lesson_day_filter.dart' show SelectBox;
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import '../child_gate.dart';
import '../selected_child.dart';
import 'parent_report_data.dart';

/// P-6 주간 레포트. 웹 정본은 `frontend/src/routes/parent/ParentReportPage.tsx` 다.
///
/// 한 주에 있었던 일을 **한 장**으로 — 수업 → 테스트 → 숙제 순이다. 배우고,
/// 시험 보고, 숙제를 한다.
///
/// **등수·백분위·반 평균·과목별 점수는 여기 없다.** 종이 성적표를 흉내 내다
/// 이것들을 넣지 마라(「만들지 마라」). 정기고사도 선생님 전용이라 오지 않는다.
class ParentReportPage extends StatefulWidget {
  const ParentReportPage({
    super.key,
    required this.controller,
    required this.selectedChild,
    this.now,
  });

  final ParentReportController controller;
  final SelectedChild selectedChild;

  /// 테스트만 넘긴다. 처음 고를 주와 연도 선택지의 기준이다.
  final DateTime? now;

  @override
  State<ParentReportPage> createState() => _ParentReportPageState();
}

class _ParentReportPageState extends State<ParentReportPage>
    with ReappearReload<ParentReportPage> {
  late ReportWeek _week;
  late final int _thisYear;

  /// 학부모가 직접 고른 주였다면, 고른 때의 「이번 주」. 자동으로 정해진 주면 null.
  ///
  /// **들어갈 때마다 이번 주가 떠야 한다(2026-09-29).** 셸이 State 를 살려 둬서
  /// 한 번 정한 주가 며칠·몇 주씩 남았다 — 학부모가 레포트를 열면 지난달 5주가
  /// 떠 있었다. 그래서 이번 주가 바뀌면 이번 주로 돌린다. 같은 주 안에서 고른
  /// 지난 주차는 탭을 오가도 남긴다(보던 걸 뺏지 않는다).
  ReportWeek? _pickedDuring;

  ReportWeek get _thisWeek => currentReportWeek(widget.now ?? DateTime.now());

  @override
  void initState() {
    super.initState();
    final now = widget.now ?? DateTime.now();
    _thisYear = now.year;
    _week = currentReportWeek(now);
    _attach();
  }

  void _attach() {
    widget.controller.addListener(_onChanged);
    widget.selectedChild.addListener(_onSelection);
    widget.selectedChild.ensureLoaded();
    _load();
  }

  void _detach(ParentReportController c, SelectedChild sc) {
    c.removeListener(_onChanged);
    sc.removeListener(_onSelection);
  }

  @override
  void didUpdateWidget(covariant ParentReportPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.selectedChild != widget.selectedChild) {
      _detach(oldWidget.controller, oldWidget.selectedChild);
      _attach();
    }
  }

  @override
  void dispose() {
    _detach(widget.controller, widget.selectedChild);
    super.dispose();
  }

  void _load() {
    final id = widget.selectedChild.selectedStudentId;
    if (id != null) widget.controller.load((id, _week));
  }

  /// 컨트롤러의 알림에서 [_load] 를 부르지 마라 — 실패가 끝없이 다시 부른다(14-6).
  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 자녀가 바뀌어도 보던 주는 둔다.
  void _onSelection() {
    if (!mounted) return;
    _load();
    setState(() {});
  }

  @override
  void onReappear() {
    final thisWeek = _thisWeek;
    final picked = _pickedDuring;
    // 자동으로 정한 주였거나, 고른 뒤로 주가 바뀌었으면 이번 주로 돌린다.
    if ((picked == null || picked != thisWeek) && _week != thisWeek) {
      _pickedDuring = null;
      setState(() => _week = thisWeek);
    }
    _load();
  }

  void _setWeek(ReportWeek w) {
    _pickedDuring = _thisWeek;
    setState(() => _week = w);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final sc = widget.selectedChild;
    final report = c.data;
    final w = _week;
    final gate = childGate(sc);
    return SubPageScroll(
      role: '학부모',
      onRefresh: c.refresh,
      children: [
        PageTitle(
          title: '주간 레포트',
          action: TitleChildSelect(
            children: sc.children,
            selectedStudentId: sc.selectedStudentId,
            onSelect: sc.select,
          ),
        ),
        if (gate != null)
          gate
        else ...[
          // 받는 동안에도 표지는 자리를 지킨다 — 주를 바꿀 때마다 화면이 튀지 않게.
          ReportLetterhead(week: w, report: report),
          const SizedBox(height: 16),
          // 성적·클리닉 화면과 같은 연·월·주차 선택이다.
          AppCard(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: SelectBox<int>(
                    key: const Key('report-year'),
                    value: w.year,
                    items: [
                      for (final y in [_thisYear - 1, _thisYear]) (y, '$y년'),
                    ],
                    onChanged: (y) =>
                        _setWeek((year: y!, month: w.month, week: w.week)),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: SelectBox<int>(
                    key: const Key('report-month'),
                    value: w.month,
                    items: [for (var m = 1; m <= 12; m++) (m, '$m월')],
                    onChanged: (m) =>
                        _setWeek((year: w.year, month: m!, week: w.week)),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: SelectBox<int>(
                    key: const Key('report-week'),
                    value: w.week,
                    items: [for (var k = 1; k <= 5; k++) (k, '$k주차')],
                    onChanged: (k) =>
                        _setWeek((year: w.year, month: w.month, week: k!)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (report == null)
            c.status == HomeStatus.error
                ? HomeErrorView(message: c.error, onRetry: c.refresh)
                : const SizedBox(height: 160, child: FullScreenLoader())
          else
            ..._sections(report),
        ],
      ],
    );
  }

  List<Widget> _sections(WeeklyReport r) => [
    // 레포트는 지난 주의 기록이라 「아직 안 한 것」이 아니다 — 구획은 남색이다.
    // 이 화면의 주황은 표지의 주차와 헤어라인이 맡는다.
    const SectionHead(tone: SectionTone.brand, title: '수업'),
    if (r.lessons.isEmpty)
      const ReportEmpty(text: '이 주에 공개된 수업이 없습니다.')
    else
      for (var i = 0; i < r.lessons.length; i++) ...[
        if (i > 0) const SizedBox(height: 12),
        ReportLessonCard(lesson: r.lessons[i]),
      ],
    const SizedBox(height: 16),
    const SectionHead(tone: SectionTone.brand, title: '테스트 결과'),
    if (r.tests.isEmpty)
      const ReportEmpty(text: '이 주에 기록된 테스트가 없습니다.')
    else
      ReportTestCard(tests: r.tests),
    const SizedBox(height: 16),
    const SectionHead(tone: SectionTone.brand, title: '숙제'),
    if (r.homeworks.isEmpty)
      const ReportEmpty(text: '이 주에 나간 숙제가 없습니다.')
    else
      ReportHomeworkCard(items: r.homeworks),
  ];
}

/// 표지. 이 화면에서 과감한 건 여기 하나다 — 누구의, 어느 반, 몇 주차 레포트인가.
/// 여러 자녀를 둔 학부모에게는 **지금 누구를 보고 있는지**가 그 자체로 정보다.
///
/// 주황은 주차와 헤어라인 두 곳뿐이다(accent 허용 목록의 「지금 보고 있는 주」).
/// 요약 숫자까지 주황으로 칠하지 마라.
///
/// **요약은 다섯 칸이 한계다.** 360px 에서 여섯째를 넣으면 라벨이 줄바꿈된다.
class ReportLetterhead extends StatelessWidget {
  const ReportLetterhead({super.key, required this.week, required this.report});

  final ReportWeek week;

  /// 받는 중이면 null — 이름과 숫자 자리에 「—」를 둔다.
  final WeeklyReport? report;

  @override
  Widget build(BuildContext context) {
    final r = report;
    final stats = [
      ('수업', r == null ? '—' : '${r.lessons.length}회'),
      ('클리닉', r == null ? '—' : '${r.clinicCount}회'),
      ('출석', r == null ? '—' : '${r.attended}회'),
      ('테스트', r == null ? '—' : '${r.tests.length}건'),
      ('숙제', r == null ? '—' : '${r.doneHomeworks}/${r.homeworks.length}'),
    ];
    final rooms = r?.classRooms ?? const <String>[];
    return Container(
      key: const Key('report-letterhead'),
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.brand900,
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        boxShadow: AppShadows.card,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '주간 레포트',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.54,
                    color: AppColors.brand300,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            r?.name ?? '—',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 26,
                              height: 1,
                              fontWeight: FontWeight.w700,
                              letterSpacing: -0.52,
                              color: Colors.white,
                            ),
                          ),
                          // 반이 둘 이상인 학생이 있다. 가운뎃점으로 한 줄에 둔다.
                          if (rooms.isNotEmpty) ...[
                            const SizedBox(height: 8),
                            Text(
                              rooms.join(' · '),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.brand200,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text.rich(
                      key: const Key('report-week-label'),
                      TextSpan(
                        text: '${week.month}월 ',
                        children: [
                          TextSpan(
                            text: '${week.week}주',
                            style: const TextStyle(color: AppColors.accent400),
                          ),
                        ],
                      ),
                      style: const TextStyle(
                        fontSize: 20,
                        height: 1,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Container(height: 3, color: AppColors.accent500),
          ColoredBox(
            color: AppColors.brand950.withValues(alpha: 0.5),
            child: IntrinsicHeight(
              child: Row(
                children: [
                  for (var i = 0; i < stats.length; i++) ...[
                    if (i > 0)
                      Container(
                        width: 1,
                        color: Colors.white.withValues(alpha: 0.1),
                      ),
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 4,
                          vertical: 12,
                        ),
                        child: Column(
                          children: [
                            Text(
                              stats[i].$2,
                              key: ValueKey('report-stat-${stats[i].$1}'),
                              style: const TextStyle(
                                fontSize: 16,
                                height: 1,
                                fontWeight: FontWeight.w700,
                                color: Colors.white,
                                fontFeatures: kTabularFigures,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              stats[i].$1,
                              softWrap: false,
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.brand300,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 카드가 하나도 없는 주. 빈 화면 대신 왜 비었는지를 적는다.
class ReportEmpty extends StatelessWidget {
  const ReportEmpty({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => AppCard(
    padding: const EdgeInsets.all(24),
    child: Text(
      text,
      textAlign: TextAlign.center,
      style: const TextStyle(fontSize: 14, color: AppColors.slate500),
    ),
  );
}

/// 그 주 수업 한 번. **영상은 없다** — 학부모는 못 본다(서버가 비운다). 자리도
/// 만들지 마라. 네 칸은 각각 비어 있을 수 있고, 없는 칸은 통째로 숨긴다.
class ReportLessonCard extends StatelessWidget {
  const ReportLessonCard({super.key, required this.lesson});

  final ParentLessonDetail lesson;

  @override
  Widget build(BuildContext context) {
    final status = lesson.attendance ?? DayStatus.pending;
    final style = dayStatusStyle(status);
    final notes = lesson.notes;
    final blocks = <Widget>[
      if (notes.content != null) _Block(label: '수업 내용', text: notes.content!),
      // 중점 사항만 배경을 깐다. 선생님이 「이건 꼭 보세요」로 쓰는 칸이다.
      if (notes.keyPoints != null)
        _Block(label: '중점 사항', text: notes.keyPoints!, highlight: true),
      if (notes.homeworkNote != null)
        _Block(label: '수업 숙제', text: notes.homeworkNote!),
      if (notes.clinicNote != null)
        _Block(label: '클리닉', text: notes.clinicNote!),
    ];
    return AppCard(
      key: ValueKey('report-lesson-${lesson.lessonId}'),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${lesson.lessonDate.substring(5).replaceFirst('-', '.')} '
                      '(${dayOfWeekLabel(lesson.lessonDate)})',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.1,
                        color: AppColors.slate400,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      lesson.title ?? '제목 없음',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.16,
                        color: AppColors.brand900,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              // 출결은 캘린더(P-2)와 같은 규칙·같은 색이다. null 은 결석이 아니라 확정 전이다.
              Container(
                key: const Key('report-attendance'),
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: style.background,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  lesson.attendance == null ? '출석 미확인' : status.label,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: style.foreground,
                  ),
                ),
              ),
            ],
          ),
          // 영상 시청 현황(2026-09-29). 영상은 학부모에게 안 보이고, 봤는지만 보인다(웹과 같다).
          if (lesson.videoWatch case final watch?) ...[
            const SizedBox(height: 8),
            Text.rich(
              key: const Key('report-video-watch'),
              TextSpan(
                text: '수업 영상 · ',
                children: [
                  TextSpan(
                    text: watch.label,
                    style: const TextStyle(
                      fontWeight: FontWeight.w500,
                      color: AppColors.slate700,
                    ),
                  ),
                ],
              ),
              style: const TextStyle(fontSize: 12, color: AppColors.slate500),
            ),
          ],
          if (blocks.isNotEmpty) ...[
            const SizedBox(height: 12),
            for (var i = 0; i < blocks.length; i++)
              Container(
                padding: const EdgeInsets.only(top: 12),
                margin: EdgeInsets.only(top: i == 0 ? 0 : 12),
                decoration: const BoxDecoration(
                  border: Border(top: BorderSide(color: AppColors.slate100)),
                ),
                child: blocks[i],
              ),
          ],
        ],
      ),
    );
  }
}

class _Block extends StatelessWidget {
  const _Block({
    required this.label,
    required this.text,
    this.highlight = false,
  });

  final String label;
  final String text;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final body = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.13,
            color: highlight ? AppColors.accent700 : AppColors.brand900,
          ),
        ),
        const SizedBox(height: 4),
        // 줄바꿈은 선생님이 쓴 그대로 살린다.
        Text(
          text,
          style: const TextStyle(
            fontSize: 14,
            height: 1.625,
            color: AppColors.slate700,
          ),
        ),
      ],
    );
    if (!highlight) return body;
    return Container(
      key: const Key('report-keypoints'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.accent50,
        borderRadius: BorderRadius.circular(AppRadii.xl),
        border: const Border(
          left: BorderSide(color: AppColors.accent500, width: 3),
        ),
      ),
      child: body,
    );
  }
}

/// 그 주 테스트 결과 + 그때까지의 흐름. 막대는 본인 것 하나다 — 반 평균·등수
/// 막대를 옆에 세우지 마라.
class ReportTestCard extends StatelessWidget {
  const ReportTestCard({super.key, required this.tests});

  final List<WeekTest> tests;

  @override
  Widget build(BuildContext context) {
    // 그래프 종류는 서버가 정한다(chartKind). 두 주 이상 쌓여야 흐름이다.
    final charts = tests
        .where((t) => t.section.chartKind != ChartKind.none)
        .where((t) => t.history.length >= 2);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppCard(
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              for (var i = 0; i < tests.length; i++)
                Container(
                  key: ValueKey('report-test-${tests[i].section.testType}'),
                  decoration: i == 0
                      ? null
                      : const BoxDecoration(
                          border: Border(
                            top: BorderSide(color: AppColors.slate100),
                          ),
                        ),
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        tests[i].section.label,
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppColors.brand900,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(child: ScoreValueWithBadge(item: tests[i].item)),
                    ],
                  ),
                ),
            ],
          ),
        ),
        for (final t in charts) ...[
          const SizedBox(height: 8),
          AppCard(
            key: ValueKey('report-chart-${t.section.testType}'),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Expanded(
                      child: Text(
                        '${t.section.label} 추이',
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w800,
                          color: AppColors.brand900,
                        ),
                      ),
                    ),
                    Text(
                      '${t.item.weekLabel}까지',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.slate400,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                CorrectCountChart(
                  items: t.history,
                  chartKind: t.section.chartKind,
                  highlight: t.item.key,
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }
}

/// 그 주에 나간 숙제와 결과. **숙제 지시문·사진은 없다** — 사진은 P-3 에서 본다.
/// GRID 는 채점축으로 그린다([lessonHomeworkBadge]).
class ReportHomeworkCard extends StatelessWidget {
  const ReportHomeworkCard({super.key, required this.items});

  final List<(String, LessonHomework)> items;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          for (var i = 0; i < items.length; i++)
            Builder(
              builder: (context) {
                final (date, h) = items[i];
                final (label, tone) = lessonHomeworkBadge(h);
                return Container(
                  key: ValueKey('report-homework-${h.homeworkId}'),
                  decoration: i == 0
                      ? null
                      : const BoxDecoration(
                          border: Border(
                            top: BorderSide(color: AppColors.slate100),
                          ),
                        ),
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              h.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                                color: AppColors.brand900,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${date.substring(5).replaceFirst('-', '.')} 수업',
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.slate400,
                                fontFeatures: kTabularFigures,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      AppBadge(tone: tone, label: label),
                    ],
                  ),
                );
              },
            ),
        ],
      ),
    );
  }
}
