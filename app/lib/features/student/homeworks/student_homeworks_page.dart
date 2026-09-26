import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/lesson_day_filter.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../student/home/student_home_controller.dart' show HomeStatus;
import 'student_homework_controllers.dart';
import 'student_homework_models.dart';

/// S-2 숙제 목록. 웹 정본은 `frontend/src/routes/student/StudentHomeworkPage.tsx` 다.
///
/// **세 구획이다(CLAUDE.md 4-7)** — 「이번 주에 낼 것 / 다시 제출 필요 / 지난 숙제」.
///
/// - 첫 구획은 줄 목록이 아니라 **선생님이 수업에 적은 수업 숙제 글**이고
///   반마다 한 덩어리다. 수업을 공개하기 전에는 안 보인다(서버가 거른다).
/// - 둘째 구획의 판정은 [StudentHomeworkItem.isTodo] 다 — GRID 는
///   `resubmitRequired`, ONLINE 은 `status == NOT_SUBMITTED`. 한 축으로 줄이지 마라.
/// - 위 두 구획만 주황이다. 「지금 내야 하는 것」과 「이미 끝난 것」이 같은 무게로
///   보이면 학생이 목록을 끝까지 읽어야 할 일을 찾는다.
/// - 지난 숙제는 **수업일별**로 묶인다. 날짜가 그룹 머리로 올라가므로 줄 안에는
///   반 이름만 남는다.
///
/// 위 두 구획은 **달 필터를 따르지 않는다**(컨트롤러 주석).
class StudentHomeworksPage extends StatefulWidget {
  const StudentHomeworksPage({super.key, required this.controller});

  final StudentHomeworksController controller;

  @override
  State<StudentHomeworksPage> createState() => _StudentHomeworksPageState();
}

class _StudentHomeworksPageState extends State<StudentHomeworksPage>
    with ReappearReload<StudentHomeworksPage> {
  late int _year;
  int? _month;
  DayChoice _day = DayChoice.everything;

  MonthFilter get _filter =>
      _month == null ? MonthFilter.all : MonthFilter(_year, _month);

  @override
  void initState() {
    super.initState();
    // 세션 안에서 다시 그려질 때(로그아웃 없이 화면이 새로 만들어질 때)
    // 컨트롤러가 들고 있는 달을 이어받는다. 처음이면 전체 월이다.
    final kept = widget.controller.param;
    _year = kept?.year ?? DateTime.now().year;
    _month = kept?.month;
    widget.controller.addListener(_onChanged);
    widget.controller.load(_filter);
  }

  /// 탭 재진입·상세에서 돌아옴·앱 복귀. 60초 안이면 컨트롤러가 무시한다 —
  /// 여기서 조건을 다시 만들지 마라.
  @override
  void onReappear() => widget.controller.load(_filter);

  @override
  void didUpdateWidget(covariant StudentHomeworksPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load(_filter);
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

  /// 달·연도를 바꾸면 고른 수업일 칩을 버린다 — 그 달에 없는 날을 들고 가면
  /// 빈 화면이 뜬다.
  void _changeFilter({int? year, required int? month}) {
    setState(() {
      if (year != null) _year = year;
      _month = month;
      _day = DayChoice.everything;
    });
    widget.controller.load(_filter);
  }

  void _openDetail(StudentHomeworkItem item) =>
      context.go('${AppRoutes.studentHomeworks}/${item.homeworkId}');

  void _showDescription(StudentHomeworkItem item) =>
      showDescriptionSheet(context, item.title, item.description ?? '');

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    final todo = data?.todo ?? const <StudentHomeworkItem>[];
    final notes = data?.notes ?? const <HomeworkNote>[];
    // 지난 숙제만 날짜로 묶는다. 할 일은 위 주황 구획에 이미 있어서 여기
    // 또 넣으면 같은 줄이 화면에 두 번 나온다.
    final groups = groupByLessonDay(
      (data?.items ?? const <StudentHomeworkItem>[])
          .where((h) => !h.isTodo)
          .toList(),
      (h) => h.lessonDate,
    );
    final shown = groups.where((g) => _day.matches(g.lessonDate)).toList();

    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '숙제'),
        LessonDayFilter<StudentHomeworkItem>(
          year: _year,
          month: _month,
          selectedDay: _day,
          groups: groups,
          onYearChange: (y) => _changeFilter(year: y, month: _month),
          onMonthChange: (m) => _changeFilter(month: m),
          onDayChange: (d) => setState(() => _day = d),
        ),
        const SizedBox(height: 16),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          if (notes.isNotEmpty) ...[
            _notesSection(notes),
            const SizedBox(height: 20),
          ],
          if (todo.isNotEmpty) ...[
            _todoSection(todo),
            const SizedBox(height: 20),
          ],
          if (shown.isEmpty)
            TintBlock(
              key: const Key('homeworks-empty'),
              tone: SectionTone.neutral,
              children: [
                EmptyNote(
                  text: todo.isNotEmpty || notes.isNotEmpty
                      ? '지난 숙제가 없습니다.'
                      : '받은 숙제가 없습니다.',
                ),
              ],
            )
          else
            for (final group in shown) ...[
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SectionHead(tone: SectionTone.neutral, title: group.label),
                  TintBlock(
                    tone: SectionTone.neutral,
                    children: [
                      for (final item in group.items)
                        HomeworkRow(
                          item: item,
                          accent: false,
                          onTap: () => _openDetail(item),
                          onShowDescription: () => _showDescription(item),
                        ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),
            ],
        ],
      ],
    );
  }

  Widget _notesSection(List<HomeworkNote> notes) => Column(
    key: const Key('notes-section'),
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      const SectionHead(tone: SectionTone.accent, title: '이번 주에 낼 것'),
      TintBlock(
        tone: SectionTone.accent,
        children: [
          for (final note in notes)
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 반 이름과 주차가 먼저다. 여러 반에 속한 학생은 어느 반
                  // 숙제인지 모르면 글을 읽어도 쓸 수 없다.
                  Text(
                    '${note.classRoomName} · ${note.weekLabel} · '
                    '${note.lessonDate} 수업',
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      color: AppColors.accent700.withValues(alpha: 0.75),
                      fontFeatures: kTabularFigures,
                    ),
                  ),
                  const SizedBox(height: 4),
                  // 줄바꿈은 선생님이 쓴 그대로 살린다(Text 가 \n 을 지킨다).
                  Text(
                    note.homeworkNote,
                    style: const TextStyle(
                      fontSize: 14,
                      height: 1.6,
                      color: AppColors.brand900,
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    ],
  );

  Widget _todoSection(List<StudentHomeworkItem> todo) => Column(
    key: const Key('todo-section'),
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      SectionHead(
        tone: SectionTone.accent,
        title: '다시 제출 필요',
        count: todo.length,
      ),
      TintBlock(
        tone: SectionTone.accent,
        children: [
          for (final item in todo)
            HomeworkRow(
              item: item,
              accent: true,
              onTap: () => _openDetail(item),
              onShowDescription: () => _showDescription(item),
            ),
        ],
      ),
    ],
  );
}

/// 목록의 한 줄. 두 구획이 같은 줄 모양을 쓰고 **배경만 다르다** — 줄 구조까지
/// 달라지면 같은 것을 두 가지로 그린 꼴이 된다.
///
/// 주황 구획은 날짜 그룹 밖이라 줄 안에 수업일이 남는다. 회색 구획은 그룹
/// 머리가 이미 날짜라 반 이름만 있으면 된다.
class HomeworkRow extends StatelessWidget {
  const HomeworkRow({
    super.key,
    required this.item,
    required this.accent,
    required this.onTap,
    required this.onShowDescription,
  });

  final StudentHomeworkItem item;
  final bool accent;
  final VoidCallback onTap;
  final VoidCallback onShowDescription;

  @override
  Widget build(BuildContext context) {
    final remaining = item.remainingMinutes;
    final description = item.description;
    return Material(
      key: ValueKey('homework-${item.homeworkId}'),
      // InkWell 의 물결이 TintBlock 의 틴트를 덮지 않게 투명 Material 위에 그린다.
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        highlightColor: accent
            ? AppColors.accent100.withValues(alpha: 0.6)
            : AppColors.slate50,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.225,
                        color: AppColors.brand900,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  _statusBadge(),
                ],
              ),
              const SizedBox(height: 2),
              Text(
                accent && item.lessonDate != null
                    ? '${item.classRoomName} · ${item.lessonDate} 수업'
                    : item.classRoomName,
                style: TextStyle(
                  fontSize: 11.5,
                  color: accent
                      ? AppColors.accent700.withValues(alpha: 0.75)
                      : AppColors.slate500,
                  fontFeatures: kTabularFigures,
                ),
              ),
              // ONLINE 의 남은 시간은 위 배지가 이미 보여준다. GRID 는 재제출을
              // 연 열에만 마감이 있고, 없으면 남은 시간도 없다 — 0 을 보여주면
              // 「마감 임박」으로 읽힌다. 지난 숙제 구획에서는 회색이다 — 다 낸
              // 숙제 옆의 주황은 「아직 안 한 것」이라는 뜻을 거짓으로 만든다.
              if (item.isGrid && remaining != null) ...[
                const SizedBox(height: 4),
                Text(
                  remainingLabel(remaining),
                  key: const Key('grid-remaining'),
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w600,
                    color: accent ? AppColors.accent600 : AppColors.slate500,
                    fontFeatures: kTabularFigures,
                  ),
                ),
              ],
              // 상세 내용이 있는 줄만(4-8). 줄 전체가 눌리는 곳이라, 이 링크는
              // 자기 InkWell 이 먼저 먹는다.
              if (description != null && description.isNotEmpty) ...[
                const SizedBox(height: 6),
                InkWell(
                  key: const Key('show-description'),
                  onTap: onShowDescription,
                  child: const Text(
                    '내용 보기',
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      color: AppColors.brand600,
                      decoration: TextDecoration.underline,
                      decorationColor: AppColors.brand600,
                    ),
                  ),
                ),
              ],
              if (_tags.isNotEmpty) ...[
                const SizedBox(height: 6),
                Wrap(spacing: 4, runSpacing: 4, children: _tags),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// 오른쪽 위 배지. GRID 는 채점축, ONLINE 은 제출축이다(4-2) — GRID 를
  /// status 로 그리면 다 해온 학생이 「미제출」로 보인다.
  Widget _statusBadge() {
    if (item.isGrid) {
      return AppBadge(
        tone: gradeTone(item.result),
        label: gradeLabel(
          item.result,
          item.completionRate,
          item.resolvedByResubmission,
        ),
      );
    }
    if (item.status == 'NOT_SUBMITTED') {
      final remaining = item.remainingMinutes;
      final overdue = remaining != null && remaining < 0;
      return AppBadge(
        tone: overdue ? BadgeTone.danger : BadgeTone.warn,
        label: remaining != null
            ? remainingLabel(remaining)
            : submissionLabel(item.status),
      );
    }
    return AppBadge(tone: BadgeTone.ok, label: submissionLabel(item.status));
  }

  List<Widget> get _tags => [
    // GRID 는 resubmitRequired 가 열려야만 다시 낼 수 있다. ONLINE 의 상태는
    // 위 배지가 이미 알려주므로 GRID 재제출만 따로 짚는다.
    if (item.isGrid && item.resubmitRequired)
      const AppBadge(tone: BadgeTone.warn, label: '다시 제출 필요'),
    if (item.isLate) const AppBadge(label: '늦게 냄'),
    if (item.photoCount > 0) AppBadge(label: '사진 ${item.photoCount}장'),
    if (item.hasVideo) const AppBadge(label: '영상'),
  ];
}

/// 재제출 상세 내용(4-8). 웹의 아래에서 올라오는 모달과 같다. 학생만 본다 —
/// 학부모 화면에서 부르지 마라.
Future<void> showDescriptionSheet(
  BuildContext context,
  String title,
  String description,
) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.white,
    // 탭 바 위로 올라와야 한다 — 셸 안의 Navigator 가 아니라 앱 전체 위에 띄운다.
    useRootNavigator: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.8,
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          key: const Key('description-sheet'),
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                  color: AppColors.brand900,
                ),
              ),
              const SizedBox(height: 8),
              // 줄바꿈은 선생님이 쓴 그대로 살린다.
              Text(
                description,
                style: const TextStyle(
                  fontSize: 14,
                  height: 1.6,
                  color: AppColors.slate700,
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('닫기'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
