import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../shared/widgets/attendance_calendar.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../child_gate.dart';
import '../selected_child.dart';
import 'parent_schedule_data.dart';

/// P-2 수업 · 클리닉 일정. 웹 정본은 `frontend/src/routes/parent/ParentSchedulePage.tsx` 다.
///
/// **조회만이다** — 신청·취소·변경 버튼을 두지 마라.
///
/// 자녀가 클리닉을 바꾸면 **공지가 한 건 발행된다**(10-1). 여기에 변경 이력을
/// 따로 그리지 마라 — 공지와 같은 내용이 두 곳에 남는다.
///
/// 학생 출석(S-6)과 같은 캘린더다. 두 화면이 어긋나면 「엄마 폰에는 다르게
/// 나온다」는 문의가 된다.
class ParentSchedulePage extends StatefulWidget {
  const ParentSchedulePage({
    super.key,
    required this.controller,
    required this.selectedChild,
  });

  final ParentScheduleController controller;
  final SelectedChild selectedChild;

  @override
  State<ParentSchedulePage> createState() => _ParentSchedulePageState();
}

class _ParentSchedulePageState extends State<ParentSchedulePage>
    with ReappearReload<ParentSchedulePage> {
  late YearMonth _month;

  @override
  void initState() {
    super.initState();
    // 세션 동안 보던 달로 돌아온다. 처음이면 이번 달(표시용이라 기기 시계).
    final now = DateTime.now();
    _month = widget.controller.param?.$2 ?? (year: now.year, month: now.month);
    _attach();
  }

  void _attach() {
    widget.controller.addListener(_onChanged);
    widget.selectedChild.addListener(_onSelection);
    widget.selectedChild.ensureLoaded();
    _load();
  }

  void _detach(ParentScheduleController c, SelectedChild sc) {
    c.removeListener(_onChanged);
    sc.removeListener(_onSelection);
  }

  @override
  void didUpdateWidget(covariant ParentSchedulePage oldWidget) {
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
    if (id != null) widget.controller.load((id, _month));
  }

  /// 컨트롤러의 알림에서 [_load] 를 부르지 마라 — 실패가 끝없이 다시 부른다.
  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 자녀가 바뀌어도 보던 달은 둔다 — 둘째의 같은 달을 보려고 바꾼 것이다.
  void _onSelection() {
    if (!mounted) return;
    _load();
    setState(() {});
  }

  @override
  void onReappear() => _load();

  void _shift(int delta) {
    setState(() => _month = shiftMonth(_month, delta));
    _load();
  }

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
          title: '수업 · 클리닉 일정',
          action: TitleChildSelect(
            children: sc.children,
            selectedStudentId: sc.selectedStudentId,
            onSelect: sc.select,
          ),
        ),
        if (childGate(sc) case final gate?)
          gate
        else if (data != null)
          AttendanceCalendar(
            data: data,
            onPrev: () => _shift(-1),
            onNext: () => _shift(1),
          )
        else if (c.status == HomeStatus.error)
          HomeErrorView(message: c.error, onRetry: c.refresh)
        else
          const SizedBox(height: 160, child: FullScreenLoader()),
        const PendingFootnote(),
      ],
    );
  }
}
