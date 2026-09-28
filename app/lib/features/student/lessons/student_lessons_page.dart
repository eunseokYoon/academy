import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/lesson_day_filter.dart' show SelectBox;
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import 'student_lesson_data.dart';

/// S-5 목록 「수업영상 및 레포트」. 웹 정본은 `frontend/src/routes/student/StudentLessonPage.tsx`.
///
/// 재원 기간 밖·미공개 수업은 서버가 거른다. 앱에서 다시 거르지 마라.
/// **시청 여부는 없다**(V14) — 영상이 있는지만 말한다.
class StudentLessonsPage extends StatefulWidget {
  const StudentLessonsPage({
    super.key,
    required this.controller,
    this.thisYear,
  });

  final StudentLessonsController controller;

  /// 테스트만 넘긴다. 연도 선택지(작년·올해)의 기준이다 — 표시용이라 기기 시계.
  final int? thisYear;

  @override
  State<StudentLessonsPage> createState() => _StudentLessonsPageState();
}

class _StudentLessonsPageState extends State<StudentLessonsPage>
    with ReappearReload<StudentLessonsPage> {
  late LessonQuery _query;

  int get _thisYear => widget.thisYear ?? DateTime.now().year;

  @override
  void initState() {
    super.initState();
    _query = widget.controller.param ?? (year: _thisYear, month: null, page: 0);
    widget.controller.addListener(_onChanged);
    _load();
  }

  @override
  void didUpdateWidget(covariant StudentLessonsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      _load();
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

  void _load() => widget.controller.load(_query);

  @override
  void onReappear() => _load();

  /// 연·월을 바꾸면 첫 페이지로 돌아간다.
  void _set(LessonQuery q) {
    setState(() => _query = q);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    final q = _query;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '수업영상 및 레포트'),
        AppCard(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Expanded(
                child: SelectBox<int>(
                  key: const Key('lessons-year'),
                  value: q.year,
                  items: [
                    for (final y in [_thisYear - 1, _thisYear]) (y, '$y년'),
                  ],
                  onChanged: (y) => _set((year: y!, month: q.month, page: 0)),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: SelectBox<int?>(
                  key: const Key('lessons-month'),
                  value: q.month,
                  items: [
                    (null, '전체 월'),
                    for (var m = 1; m <= 12; m++) (m, '$m월'),
                  ],
                  onChanged: (m) => _set((year: q.year, month: m, page: 0)),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else if (data.items.isEmpty)
          const TintBlock(
            key: Key('lessons-empty'),
            tone: SectionTone.neutral,
            children: [EmptyNote(text: '공개된 수업이 없습니다.')],
          )
        else
          TintBlock(
            tone: SectionTone.neutral,
            children: [
              for (final lesson in data.items)
                _LessonRow(
                  lesson: lesson,
                  onTap: () => context.go(
                    '${AppRoutes.studentLessons}/${lesson.lessonId}',
                  ),
                ),
            ],
          ),
        if (data != null && data.totalPages > 1) ...[
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OutlinedButton(
                key: const Key('lessons-prev'),
                onPressed: q.page == 0
                    ? null
                    : () => _set((
                        year: q.year,
                        month: q.month,
                        page: q.page - 1,
                      )),
                child: const Text('이전'),
              ),
              const SizedBox(width: 12),
              Text(
                '${q.page + 1} / ${data.totalPages}',
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.slate500,
                  fontFeatures: kTabularFigures,
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                key: const Key('lessons-next'),
                onPressed: q.page + 1 >= data.totalPages
                    ? null
                    : () => _set((
                        year: q.year,
                        month: q.month,
                        page: q.page + 1,
                      )),
                child: const Text('다음'),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

class _LessonRow extends StatelessWidget {
  const _LessonRow({required this.lesson, required this.onTap});

  final StudentLessonListItem lesson;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final homeworkTitle = lesson.homeworkTitle;
    return Material(
      key: ValueKey('lesson-${lesson.lessonId}'),
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        highlightColor: AppColors.slate50,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (lesson.isNew) ...[
                    const AppBadge(tone: BadgeTone.danger, label: 'NEW'),
                    const SizedBox(width: 8),
                  ],
                  Flexible(
                    child: Text(
                      '${lesson.lessonDate.replaceAll('-', '.')} · ${lesson.classRoomName}',
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: AppColors.slate500,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                lesson.title ?? '제목 없음',
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.225,
                  color: AppColors.brand900,
                ),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  AppBadge(
                    tone: lesson.hasVideo ? BadgeTone.ok : BadgeTone.neutral,
                    label: lesson.hasVideo ? '영상 있음' : '영상 없음',
                  ),
                  if (homeworkTitle != null)
                    AppBadge(label: '숙제 · $homeworkTitle'),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
