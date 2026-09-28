import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../shared/widgets/attendance_calendar.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import 'student_attendance_data.dart';

/// S-6 출석 현황. 웹 정본은 `frontend/src/routes/student/StudentAttendancePage.tsx` 다.
///
/// **학부모 일정(P-2)과 같은 데이터·같은 구성이다** — 캘린더 칸에 수업과
/// 클리닉을 같은 칩으로 넣는다. 두 화면이 어긋나면 「엄마 폰에는 다르게
/// 나온다」는 문의가 된다.
class StudentAttendancePage extends StatefulWidget {
  const StudentAttendancePage({super.key, required this.controller});

  final StudentAttendanceController controller;

  @override
  State<StudentAttendancePage> createState() => _StudentAttendancePageState();
}

class _StudentAttendancePageState extends State<StudentAttendancePage>
    with ReappearReload<StudentAttendancePage> {
  late YearMonth _month;

  @override
  void initState() {
    super.initState();
    // 세션 동안 컨트롤러가 산다 — 보던 달이 있으면 그 달로 돌아온다.
    // 처음이면 이번 달이다(표시용 오늘이라 기기 시계를 쓴다, 14-10 의 예외와 같다).
    final now = DateTime.now();
    _month = widget.controller.param ?? (year: now.year, month: now.month);
    widget.controller.addListener(_onChanged);
    _load();
  }

  @override
  void didUpdateWidget(covariant StudentAttendancePage oldWidget) {
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

  void _load() => widget.controller.load(_month);

  @override
  void onReappear() => _load();

  void _shift(int delta) {
    setState(() => _month = shiftMonth(_month, delta));
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '출석 현황'),
        if (data != null)
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
