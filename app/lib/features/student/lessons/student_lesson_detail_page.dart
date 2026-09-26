import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';
import 'package:academy_app/shared/lib/url_opener.dart';

import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/lesson/lesson_report.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';

import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import 'student_lesson_data.dart';

/// S-5 상세. 웹 정본은 `frontend/src/routes/student/StudentLessonDetailPage.tsx`.
///
/// **영상은 앱 밖(YouTube 앱·브라우저)에서 연다.** 웹은 누르면 그 자리에
/// iframe 을 심는데, 앱에 웹뷰를 들이는 대신 서버가 만든 `embedUrl` 을 그대로
/// 기기에 넘긴다. 주소를 앱에서 다시 조립하지 마라(`YoutubeUrls.embedUrlOf`).
class StudentLessonDetailPage extends StatefulWidget {
  const StudentLessonDetailPage({
    super.key,
    required this.lessonId,
    required this.controller,
    required this.openUrl,
  });

  final int lessonId;
  final StudentLessonDetailController controller;
  final UrlOpener openUrl;

  @override
  State<StudentLessonDetailPage> createState() =>
      _StudentLessonDetailPageState();
}

class _StudentLessonDetailPageState extends State<StudentLessonDetailPage>
    with ReappearReload<StudentLessonDetailPage> {
  String? _videoError;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load(widget.lessonId);
  }

  @override
  void didUpdateWidget(covariant StudentLessonDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
    }
    if (oldWidget.controller != widget.controller ||
        oldWidget.lessonId != widget.lessonId) {
      _videoError = null;
      widget.controller.load(widget.lessonId);
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

  @override
  void onReappear() => widget.controller.load(widget.lessonId);

  Future<void> _play(LessonVideo video) async {
    final url = video.embedUrl;
    if (url == null) return;
    setState(() => _videoError = null);
    var opened = false;
    try {
      opened = await widget.openUrl(Uri.parse(url));
    } catch (_) {
      opened = false;
    }
    if (!opened && mounted) {
      setState(() => _videoError = '영상을 열 수 없습니다. 잠시 후 다시 시도해 주세요.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    // 다른 수업의 옛 데이터를 그리지 않는다 — 매개변수가 바뀌면 ParamController 가
    // 비우지만, 빌드가 load 보다 먼저 올 수 있다.
    final data = c.param == widget.lessonId ? c.data : null;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        PageBackLink(
          label: '수업 목록',
          onTap: () => context.go(AppRoutes.studentLessons),
        ),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else
          ..._body(context, data),
      ],
    );
  }

  List<Widget> _body(BuildContext context, StudentLessonDetail d) {
    final attendance = d.attendance;
    final homework = d.homework;
    final notes = d.notes;
    final blocks = <Widget>[
      AppCard(
        key: const Key('lesson-header'),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  '${d.lessonDate.replaceAll('-', '.')} · ${d.classRoomName}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.slate500,
                    fontFeatures: kTabularFigures,
                  ),
                ),
                // null 이면 확정 전이다 — 배지를 안 그린다(결석이 아니다).
                if (attendance != null)
                  AppBadge(
                    tone: attendance == DayStatus.present
                        ? BadgeTone.ok
                        : BadgeTone.warn,
                    label: attendance.label,
                  ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              d.title ?? '제목 없음',
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: AppColors.brand900,
              ),
            ),
          ],
        ),
      ),
      // 영상이 없으면 영역을 통째로 숨긴다.
      if (d.videos.isNotEmpty) _Videos(videos: d.videos, onPlay: _play),
      if (_videoError != null) FormError(message: _videoError),
      if (notes.content != null) _Note(title: '수업 내용', text: notes.content!),
      if (notes.keyPoints != null)
        _Note(title: '중점 사항', text: notes.keyPoints!),
      if (notes.homeworkNote != null)
        _Note(title: '수업 숙제', text: notes.homeworkNote!),
      if (notes.clinicNote != null)
        _Note(title: '클리닉', text: notes.clinicNote!),
      if (homework != null)
        _HomeworkBlock(
          homework: homework,
          description: d.homeworkDescription,
          onTap: () => context.go(
            '${AppRoutes.studentHomeworks}/${homework.homeworkId}',
          ),
        ),
    ];
    return [
      for (var i = 0; i < blocks.length; i++) ...[
        if (i > 0) const SizedBox(height: 16),
        blocks[i],
      ],
    ];
  }
}

/// 영상 버튼 + (둘 이상이면) 목록. 영상이 하나면 목록을 그리지 않는다 — 고를 게 없다.
class _Videos extends StatelessWidget {
  const _Videos({required this.videos, required this.onPlay});

  final List<LessonVideo> videos;
  final ValueChanged<LessonVideo> onPlay;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Material(
          color: AppColors.brand900,
          borderRadius: BorderRadius.circular(AppRadii.xl),
          child: InkWell(
            key: const Key('play-lesson'),
            borderRadius: BorderRadius.circular(AppRadii.xl),
            onTap: () => onPlay(videos.first),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                '▶ 수업영상 시청하기'
                '${videos.length > 1 ? ' (${videos.length}개)' : ''}',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ),
        if (videos.length > 1)
          for (var i = 0; i < videos.length; i++) ...[
            const SizedBox(height: 6),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(AppRadii.xl),
                boxShadow: AppShadows.card,
              ),
              child: Material(
                type: MaterialType.transparency,
                child: InkWell(
                  key: ValueKey('video-$i'),
                  borderRadius: BorderRadius.circular(AppRadii.xl),
                  onTap: videos[i].embedUrl == null
                      ? null
                      : () => onPlay(videos[i]),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 10,
                    ),
                    child: Row(
                      children: [
                        Text(
                          '${i + 1}',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            color: AppColors.brand400,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          // 이름이 없으면 「영상 N」 — 선생님이 매번 짓지 않아도 된다.
                          child: Text(
                            videos[i].title ?? '영상 ${i + 1}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 14,
                              color: AppColors.brand900,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
      ],
    );
  }
}

class _Note extends StatelessWidget {
  const _Note({required this.title, required this.text});

  final String title;
  final String text;

  @override
  Widget build(BuildContext context) => AppCard(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: AppColors.brand900,
          ),
        ),
        const SizedBox(height: 8),
        // 줄바꿈은 선생님이 쓴 그대로다. 마크다운으로 그리지 마라.
        Text(
          text,
          style: const TextStyle(fontSize: 14, color: AppColors.slate700),
        ),
      ],
    ),
  );
}

class _HomeworkBlock extends StatelessWidget {
  const _HomeworkBlock({
    required this.homework,
    required this.description,
    required this.onTap,
  });

  final LessonHomework homework;
  final String? description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (label, tone) = lessonHomeworkBadge(homework);
    final dueAt = homework.dueAt;
    final description = this.description;
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        boxShadow: AppShadows.card,
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          key: const Key('lesson-homework'),
          borderRadius: BorderRadius.circular(AppRadii.xxl),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '숙제',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.brand900,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  homework.title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: AppColors.brand900,
                  ),
                ),
                if (description != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: const TextStyle(
                      fontSize: 14,
                      color: AppColors.slate600,
                    ),
                  ),
                ],
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    AppBadge(tone: tone, label: label),
                    // GRID 는 재제출을 열기 전까지 마감이 없다. 없는 걸 그리면 1970년이 뜬다.
                    if (dueAt != null)
                      Text(
                        '마감 ${formatDueAt(dueAt)}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.slate500,
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
