import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import 'online_test_data.dart';

/// S-10 목록. 웹 정본은 `StudentOnlineTestPage.tsx`. 공개·재원·시작 시각
/// 조건은 서버가 이미 걸렀다.
///
/// **아직 안 낸 것만 주황이다**(숙제 목록과 같은 규칙). 낸 것·오프라인으로 본
/// 것까지 주황이면 「지금 할 일」이라는 뜻이 사라진다.
class StudentOnlineTestsPage extends StatefulWidget {
  const StudentOnlineTestsPage({super.key, required this.controller});

  final OnlineTestListController controller;

  @override
  State<StudentOnlineTestsPage> createState() => _StudentOnlineTestsPageState();
}

class _StudentOnlineTestsPageState extends State<StudentOnlineTestsPage>
    with ReappearReload<StudentOnlineTestsPage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load();
  }

  @override
  void didUpdateWidget(covariant StudentOnlineTestsPage oldWidget) {
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

  @override
  void onReappear() => widget.controller.load();

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '온라인 테스트'),
        const AppCard(
          padding: EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Text(
            '종이 시험지를 먼저 푼 뒤 답만 입력하세요. 작성 중인 답은 자동으로 저장됩니다.',
            style: TextStyle(
              fontSize: 12,
              height: 1.5,
              color: AppColors.slate600,
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else if (data.isEmpty)
          const TintBlock(
            tone: SectionTone.neutral,
            children: [EmptyNote(text: '응시할 테스트가 없습니다.')],
          )
        else
          ..._sections(data),
      ],
    );
  }

  List<Widget> _sections(List<OnlineTestListItem> data) {
    final todo = data.where((t) => !isTakeFinished(t.status)).toList();
    final done = data.where((t) => isTakeFinished(t.status)).toList();
    return [
      if (todo.isNotEmpty) ...[
        SectionHead(
          tone: SectionTone.accent,
          title: '응시할 테스트',
          count: todo.length,
        ),
        TintBlock(
          key: const Key('tests-todo'),
          tone: SectionTone.accent,
          children: [for (final t in todo) _row(t, accent: true)],
        ),
        const SizedBox(height: 20),
      ],
      if (done.isNotEmpty) ...[
        const SectionHead(tone: SectionTone.neutral, title: '제출 완료'),
        TintBlock(
          key: const Key('tests-done'),
          tone: SectionTone.neutral,
          children: [for (final t in done) _row(t, accent: false)],
        ),
      ],
    ];
  }

  Widget _row(OnlineTestListItem t, {required bool accent}) {
    final finished = isTakeFinished(t.status);
    final remaining = t.remainingMinutes;
    return InkWell(
      key: ValueKey('test-${t.testId}'),
      onTap: () => context.go('${AppRoutes.studentOnlineTests}/${t.testId}'),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              t.title,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
                color: AppColors.brand900,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              '${t.classRoomName} · ${t.questionCount}문항',
              style: TextStyle(
                fontSize: 11.5,
                color: accent ? AppColors.accent700 : AppColors.slate500,
                fontFeatures: kTabularFigures,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                AppBadge(
                  tone: takeStatusTone(t),
                  label: takeStatusLabel(t.status),
                ),
                if (t.status == 'IN_PROGRESS')
                  AppBadge(
                    key: ValueKey('answered-${t.testId}'),
                    label: '${t.answeredCount} / ${t.questionCount} 입력',
                  ),
                // 서버가 계산한 값이다. 기기 시계로 다시 세지 않는다.
                if (!finished && remaining != null)
                  Text(
                    remainingLabel(remaining),
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      // 마감 지남도 빨강으로 올리지 않는다(빨강은 결석·위험뿐).
                      color: t.closed ? AppColors.amber700 : AppColors.slate500,
                      fontFeatures: kTabularFigures,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
