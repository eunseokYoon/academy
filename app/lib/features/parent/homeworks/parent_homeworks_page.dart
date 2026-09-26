import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/lesson_day_filter.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../student/home/student_home_controller.dart' show HomeStatus;
import '../child_gate.dart';
import '../selected_child.dart';
import 'parent_homework_data.dart';

/// P-3 숙제 제출 현황. 웹 정본은 `frontend/src/routes/parent/ParentHomeworkPage.tsx` 다.
///
/// **제출 여부·채점 결과·제출 사진**을 본다(CLAUDE.md 4-6). 사진은 2026-09-10 에
/// 열었다. **열린 것은 사진뿐이다** — 숙제 내용·재제출 상세·영상은 응답에 없다.
/// 학생 화면(S-4)의 위젯을 여기서 재사용하지 마라.
///
/// 학생 화면과 달리 「지금 낼 것」 구획이 없다 — 학부모는 내는 사람이 아니라
/// 보는 사람이라 할 일을 따로 띄울 이유가 없다. 목록은 수업일별로 묶인다.
///
/// 이 화면은 **홈의 자식 라우트**라 홈이 자녀 목록을 먼저 부른다(14-6). 그래도
/// 목록이 비어 있으면(아직 안 받았으면) 여기서 부른다.
class ParentHomeworksPage extends StatefulWidget {
  const ParentHomeworksPage({
    super.key,
    required this.controller,
    required this.selectedChild,
  });

  final ParentHomeworksController controller;
  final SelectedChild selectedChild;

  @override
  State<ParentHomeworksPage> createState() => _ParentHomeworksPageState();
}

class _ParentHomeworksPageState extends State<ParentHomeworksPage>
    with ReappearReload<ParentHomeworksPage> {
  late int _year;
  int? _month;
  DayChoice _day = DayChoice.everything;

  MonthFilter get _filter =>
      _month == null ? MonthFilter.all : MonthFilter(_year, _month);

  @override
  void initState() {
    super.initState();
    final kept = widget.controller.param?.$2;
    _year = kept?.year ?? DateTime.now().year;
    _month = kept?.month;
    _attach();
  }

  void _attach() {
    widget.controller.addListener(_onChanged);
    widget.selectedChild.addListener(_onSelection);
    widget.selectedChild.ensureLoaded();
    _load();
  }

  void _detach(ParentHomeworksController c, SelectedChild sc) {
    c.removeListener(_onChanged);
    sc.removeListener(_onSelection);
  }

  void _load() {
    final id = widget.selectedChild.selectedStudentId;
    if (id != null) widget.controller.load((id, _filter));
  }

  @override
  void onReappear() => _load();

  @override
  void didUpdateWidget(covariant ParentHomeworksPage oldWidget) {
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

  /// 자녀가 바뀌면 그 아이의 수업일은 다르다 — 고른 칩을 들고 가면 빈 화면이
  /// 뜬다(웹 주석). 칩만 버리고 달은 둔다.
  void _onSelection() {
    if (!mounted) return;
    final id = widget.selectedChild.selectedStudentId;
    if (id != null && widget.controller.param?.$1 != id) {
      _day = DayChoice.everything;
    }
    _load();
    setState(() {});
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  void _changeFilter({int? year, required int? month}) {
    setState(() {
      if (year != null) _year = year;
      _month = month;
      _day = DayChoice.everything;
    });
    _load();
  }

  void _showPhotos(ParentHomework item) {
    final id = widget.selectedChild.selectedStudentId;
    if (id == null) return;
    showPhotoSheet(
      context,
      title: item.title,
      photos: widget.controller.repository.photos(id, item.homeworkId),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final sc = widget.selectedChild;
    final id = sc.selectedStudentId;
    // 자녀·달이 바뀌면 load 가 빌드 전에 옛 목록을 버린다(ParamController).
    // 자녀가 아직 안 정해졌으면 그릴 목록이 없다.
    final items = id == null ? null : c.data;
    final groups = groupByLessonDay(
      items ?? const <ParentHomework>[],
      (h) => h.lessonDate,
    );
    final shown = groups.where((g) => _day.matches(g.lessonDate)).toList();

    return SubPageScroll(
      role: '학부모',
      onRefresh: c.refresh,
      children: [
        PageTitle(
          title: '숙제 제출 현황',
          action: TitleChildSelect(
            children: sc.children,
            selectedStudentId: id,
            onSelect: sc.select,
          ),
        ),
        LessonDayFilter<ParentHomework>(
          year: _year,
          month: _month,
          selectedDay: _day,
          groups: groups,
          onYearChange: (y) => _changeFilter(year: y, month: _month),
          onMonthChange: (m) => _changeFilter(month: m),
          onDayChange: (d) => setState(() => _day = d),
        ),
        const SizedBox(height: 16),
        const Text(
          '숙제를 누르면 자녀가 제출한 사진을 볼 수 있습니다.',
          style: TextStyle(fontSize: 12.5, color: AppColors.slate500),
        ),
        const SizedBox(height: 16),
        if (childGate(sc) case final gate?)
          gate
        else if (items == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else if (shown.isEmpty)
          const TintBlock(
            key: Key('homeworks-empty'),
            tone: SectionTone.neutral,
            children: [EmptyNote(text: '아직 받은 숙제가 없습니다.')],
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
                      _ParentHomeworkRow(
                        item: item,
                        onShowPhotos: () => _showPhotos(item),
                      ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 16),
          ],
        const SizedBox(height: 8),
        const Text(
          '숙제 내용과 제출한 사진은 학생 화면에서 확인할 수 있습니다.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 12, color: AppColors.slate400),
        ),
      ],
    );
  }
}

/// 한 줄. **사진이 있는 줄만 눌린다** — 없는 줄을 눌러 빈 시트가 뜨면 고장으로
/// 읽힌다.
class _ParentHomeworkRow extends StatelessWidget {
  const _ParentHomeworkRow({required this.item, required this.onShowPhotos});

  final ParentHomework item;
  final VoidCallback onShowPhotos;

  @override
  Widget build(BuildContext context) {
    final dueAt = item.dueAt;
    final content = Padding(
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
              if (item.photoCount > 0) ...[
                AppBadge(label: '사진 ${item.photoCount}장'),
                const SizedBox(width: 8),
              ],
              _statusBadge(),
            ],
          ),
          const SizedBox(height: 2),
          // 수업일은 그룹 머리로 올라갔다. 줄에는 반 이름만 남는다.
          Text(
            item.classRoomName,
            style: const TextStyle(
              fontSize: 11.5,
              color: AppColors.slate500,
              fontFeatures: kTabularFigures,
            ),
          ),
          // GRID 는 재제출을 열기 전까지 마감이 없다.
          if (dueAt != null) ...[
            const SizedBox(height: 2),
            Text(
              '${item.isGrid ? '다시 제출 마감' : '마감'} ${formatDueAt(dueAt)}',
              style: const TextStyle(
                fontSize: 11.5,
                color: AppColors.slate500,
                fontFeatures: kTabularFigures,
              ),
            ),
          ],
          if (item.isLate) ...[
            const SizedBox(height: 6),
            const AppBadge(tone: BadgeTone.warn, label: '늦게 냄'),
          ],
        ],
      ),
    );
    return Material(
      key: ValueKey('parent-homework-${item.homeworkId}'),
      type: MaterialType.transparency,
      child: item.photoCount > 0
          ? InkWell(
              onTap: onShowPhotos,
              highlightColor: AppColors.slate50,
              child: content,
            )
          : content,
    );
  }

  /// GRID 는 채점축으로 그린다(4-2) — status 로 그리면 ⭕를 받은 아이가
  /// 「미제출」로 보여 학부모 화면이 빨개진다. 학생 화면과 글자가 다르다
  /// (「제출」·「미제출」, 미제출이 빨강) — 웹이 그렇다.
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
    return item.status == 'NOT_SUBMITTED'
        ? const AppBadge(tone: BadgeTone.danger, label: '미제출')
        : const AppBadge(tone: BadgeTone.ok, label: '제출');
  }
}

/// 제출 사진 보기. 웹의 아래에서 올라오는 모달이다. 사진만 보여준다.
Future<void> showPhotoSheet(
  BuildContext context, {
  required String title,
  required Future<List<String>> photos,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.85,
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          key: const Key('photo-sheet'),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  color: AppColors.brand900,
                ),
              ),
              const SizedBox(height: 12),
              FutureBuilder<List<String>>(
                future: photos,
                builder: (context, snap) {
                  if (snap.hasError) {
                    final e = snap.error;
                    return FormError(
                      message: e is ApiException
                          ? e.message
                          : '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
                    );
                  }
                  final urls = snap.data;
                  if (urls == null) {
                    return const SizedBox(
                      height: 120,
                      child: FullScreenLoader(),
                    );
                  }
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final url in urls) ...[
                        ClipRRect(
                          borderRadius: BorderRadius.circular(AppRadii.xl),
                          child: Image.network(
                            url,
                            key: ValueKey('sheet-photo-$url'),
                            fit: BoxFit.fitWidth,
                            errorBuilder: (context, error, stackTrace) =>
                                const SizedBox(height: 80),
                          ),
                        ),
                        const SizedBox(height: 8),
                      ],
                    ],
                  );
                },
              ),
              const SizedBox(height: 8),
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
