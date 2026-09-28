import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../shared/score/exam_dday_list.dart';
import '../../../shared/score/score_section_list.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../child_gate.dart';
import '../selected_child.dart';
import 'parent_score_data.dart';

/// P-4 테스트 결과. 웹 정본은 `frontend/src/routes/parent/ParentScorePage.tsx`.
///
/// 학생 화면(S-7)과 같은 [ScoreSectionList] 다. 순서만 다르다 — 학부모는 성적이
/// 먼저, 시험 일정이 아래다(웹과 같다). 학부모가 보려는 건 자녀의 흐름이다.
class ParentScoresPage extends StatefulWidget {
  const ParentScoresPage({
    super.key,
    required this.controller,
    required this.selectedChild,
  });

  final ParentScoreController controller;
  final SelectedChild selectedChild;

  @override
  State<ParentScoresPage> createState() => _ParentScoresPageState();
}

class _ParentScoresPageState extends State<ParentScoresPage>
    with ReappearReload<ParentScoresPage> {
  @override
  void initState() {
    super.initState();
    _attach();
  }

  void _attach() {
    widget.controller.addListener(_onChanged);
    widget.selectedChild.addListener(_onSelection);
    widget.selectedChild.ensureLoaded();
    _load();
  }

  void _detach(ParentScoreController c, SelectedChild sc) {
    c.removeListener(_onChanged);
    sc.removeListener(_onSelection);
  }

  @override
  void didUpdateWidget(covariant ParentScoresPage oldWidget) {
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
    if (id != null) widget.controller.load(id);
  }

  /// 컨트롤러의 알림에서 [_load] 를 부르지 마라 — 실패가 끝없이 다시 부른다(14-6).
  void _onChanged() {
    if (mounted) setState(() {});
  }

  void _onSelection() {
    if (!mounted) return;
    _load();
    setState(() {});
  }

  @override
  void onReappear() => _load();

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final sc = widget.selectedChild;
    final data = c.data;
    return SubPageScroll(
      role: '학부모',
      onRefresh: c.refresh,
      children: [
        PageTitle(
          title: '테스트 결과',
          action: TitleChildSelect(
            children: sc.children,
            selectedStudentId: sc.selectedStudentId,
            onSelect: sc.select,
          ),
        ),
        if (childGate(sc) case final gate?)
          gate
        else if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          ScoreSectionList(data: data.$1),
          if (data.$2.isNotEmpty) ...[
            const SizedBox(height: 20),
            ExamDdayList(schedules: data.$2),
          ],
        ],
      ],
    );
  }
}
