import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/home_labels.dart';

import 'section.dart';

/// 홈의 「시험 일정」 구획. **학생 홈(S-1)과 학부모 홈(P-1)이 같은 블록이다**
/// — 웹 `ParentHomePage.tsx` 주석 「S-1과 같은 블록이다」. 원래 학생 홈
/// 파일 안의 private 메서드였는데, 학부모 홈이 같은 것을 그려야 해서 여기로
/// 옮겼다. 두 벌로 두지 마라.
///
/// 시험 범위는 D-day 와 같은 시험 이야기라 떨어뜨리지 않는다.
/// 남은 날짜는 위 지면 칸이 이미 말했으므로 여기서 다시 세지 않는다.
///
/// 모델이 아니라 값을 받는다 — 공용 위젯이 `features/` 를 알면 안 된다.
class ExamScheduleSection extends StatelessWidget {
  const ExamScheduleSection({
    super.key,
    required this.examType,
    required this.startDate,
    required this.scopeNote,
  });

  /// 백엔드 `ExamType` 이름. 라벨은 [examLabel] 이 붙인다.
  final String examType;

  /// `2026-10-01`.
  final String startDate;

  /// 선생님이 아직 안 올렸으면 null 이다.
  final String? scopeNote;

  @override
  Widget build(BuildContext context) {
    final scopeNote = this.scopeNote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        const SectionHead(tone: SectionTone.brand, title: '시험 일정'),
        TintBlock(
          tone: SectionTone.brand,
          children: [
            Padding(
              // 웹 `px-3.5 py-3.5`.
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text.rich(
                    key: const Key('exam-title'),
                    TextSpan(
                      children: [
                        TextSpan(
                          text: examLabel(examType),
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: AppColors.brand900,
                          ),
                        ),
                        // 웹 `ml-2`.
                        const WidgetSpan(child: SizedBox(width: 8)),
                        TextSpan(
                          // 지면 칸의 sub 와 달리 **연도를 남긴다** —
                          // 여기는 자른 날짜가 아니다.
                          text: '${startDate.replaceAll('-', '.')} 시작',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w500,
                            color: AppColors.brand600.withValues(alpha: 0.8),
                            fontFeatures: kTabularFigures,
                          ),
                        ),
                      ],
                    ),
                  ),
                  // 웹 `mt-1.5`.
                  const SizedBox(height: 6),
                  // **「미정」이라고 지어내지 마라** — 대신 흐린 색으로
                  // 「미등록」을 적는다. 줄바꿈은 살린다(`maxLines` 를 주지 마라).
                  Text(
                    scopeNote ?? '시험 범위 미등록',
                    style: TextStyle(
                      fontSize: 12.5,
                      height: 1.625,
                      color: scopeNote != null
                          ? AppColors.brand950.withValues(alpha: 0.8)
                          : AppColors.brand600.withValues(alpha: 0.5),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ],
    );
  }
}
