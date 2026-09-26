import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/home_labels.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';
import '../widgets/section.dart';
import 'score_data.dart';

/// 시험 D-day. 학생(S-7)과 학부모(P-4)가 같이 쓴다. 웹 `ExamDdayList.tsx`.
///
/// **일정이 없으면 영역을 통째로 숨긴다**(14-3). 0 이나 임의 값을 그리지 마라.
/// **D-day 는 서버 값이다**(14-2).
class ExamDdayList extends StatelessWidget {
  const ExamDdayList({super.key, required this.schedules});

  final List<ExamSchedule> schedules;

  @override
  Widget build(BuildContext context) {
    if (schedules.isEmpty) return const SizedBox.shrink();
    final ordered = [
      ...schedules.where((s) => s.dDay >= 0),
      ...schedules.where((s) => s.dDay < 0),
    ];
    return Column(
      key: const Key('exam-dday-list'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SectionHead(tone: SectionTone.brand, title: '시험 일정'),
        TintBlock(
          tone: SectionTone.brand,
          children: [for (final s in ordered) _ExamRow(schedule: s)],
        ),
      ],
    );
  }
}

class _ExamRow extends StatelessWidget {
  const _ExamRow({required this.schedule});

  final ExamSchedule schedule;

  @override
  Widget build(BuildContext context) {
    // 지난 시험은 흐리게 남긴다. 지우면 「범위가 뭐였더라」를 확인할 데가 없어진다.
    final over = schedule.dDay < 0;
    final scope = schedule.scopeNote;
    final dDay = schedule.dDay;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  examLabel(schedule.examType),
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: AppColors.brand900.withValues(alpha: over ? 0.4 : 1),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${schedule.startDate.replaceAll('-', '.')} ~ '
                  '${schedule.endDate.substring(5).replaceAll('-', '.')}',
                  style: TextStyle(
                    fontSize: 11.5,
                    color: AppColors.brand600.withValues(alpha: 0.7),
                    fontFeatures: kTabularFigures,
                  ),
                ),
                if (scope != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    scope,
                    style: TextStyle(
                      fontSize: 12,
                      height: 1.625,
                      color: AppColors.brand950.withValues(alpha: 0.7),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(
            dDay >= 0 ? dDayLabel(dDay) : '${-dDay}일 전',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.51,
              color: AppColors.brand900.withValues(alpha: over ? 0.35 : 1),
              fontFeatures: kTabularFigures,
            ),
          ),
        ],
      ),
    );
  }
}
