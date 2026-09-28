import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../widgets/section.dart';
import '../widgets/sub_page.dart';
import 'correct_count_chart.dart';
import 'score_data.dart';
import 'score_value.dart';

/// 이 줄 수를 넘으면 구획 안에서 스크롤한다. 주차가 쌓여도 화면이 끝없이 길어지지 않는다.
const _visibleRows = 5;

/// S-7 · P-4 공용. **학생과 학부모가 같은 화면을 본다** — 이 위젯을 두 화면이
/// 같이 쓰는 것이 「학생도 똑같이 본다」의 보장이다. 웹 `ScoreSectionList.tsx`.
///
/// 서버가 데이터 있는 종류만 내려주므로 여기서 걸러내지 않는다. 배지 판정도
/// 서버 값 그대로다.
class ScoreSectionList extends StatelessWidget {
  const ScoreSectionList({super.key, required this.data});

  final ScoreData data;

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[
      // 빨강이 아니라 주황이다. 재시험 예정은 위험이 아니라 **학생이 아직 처리
      // 안 한 것**이다 — 미완료 숙제와 같은 뜻이라 같은 색이다(accent 허용 목록).
      if (data.retestScheduled.isNotEmpty)
        Column(
          key: const Key('retest-section'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SectionHead(
              tone: SectionTone.accent,
              title: '재시험 예정',
              count: data.retestScheduled.length,
            ),
            TintBlock(
              tone: SectionTone.accent,
              children: [
                for (final n in data.retestScheduled)
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 10,
                    ),
                    child: Text(
                      n.label,
                      style: const TextStyle(
                        fontSize: 13.5,
                        fontWeight: FontWeight.w600,
                        color: AppColors.accent700,
                      ),
                    ),
                  ),
              ],
            ),
          ],
        ),
      if (data.sections.isEmpty)
        const TintBlock(
          tone: SectionTone.neutral,
          children: [EmptyNote(text: '아직 기록된 성적이 없습니다.')],
        ),
      for (final s in data.sections) _ScoreSection(section: s),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) const SizedBox(height: 20),
          children[i],
        ],
      ],
    );
  }
}

class _ScoreSection extends StatelessWidget {
  const _ScoreSection({required this.section});

  final ScoreSection section;

  @override
  Widget build(BuildContext context) {
    // 목록은 최신이 위다. 응답은 그래프용 오름차순이라 여기서만 뒤집는다.
    final rows = section.items.reversed.toList();
    final list = Column(
      children: [
        for (var i = 0; i < rows.length; i++)
          Container(
            key: ValueKey('score-row-${section.testType}-${rows[i].key}'),
            decoration: i == 0
                ? null
                : const BoxDecoration(
                    border: Border(top: BorderSide(color: AppColors.slate100)),
                  ),
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  rows[i].weekLabel,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: AppColors.brand900,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(child: ScoreValueWithBadge(item: rows[i])),
              ],
            ),
          ),
      ],
    );
    return Column(
      key: ValueKey('score-section-${section.testType}'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHead(tone: SectionTone.brand, title: section.label),
        TintBlock(
          tone: SectionTone.neutral,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (section.chartKind != ChartKind.none) ...[
                    CorrectCountChart(
                      items: section.items,
                      chartKind: section.chartKind,
                    ),
                    const SizedBox(height: 12),
                  ],
                  // 5줄보다 조금 크게 잘라 다음 줄이 반쯤 걸치게 둔다 — 그게 「아래에
                  // 더 있다」의 유일한 신호다(웹 `max-h-48`).
                  if (rows.length > _visibleRows)
                    ConstrainedBox(
                      key: const Key('score-rows-scroll'),
                      constraints: const BoxConstraints(maxHeight: 192),
                      child: SingleChildScrollView(
                        physics: const ClampingScrollPhysics(),
                        child: list,
                      ),
                    )
                  else
                    list,
                ],
              ),
            ),
          ],
        ),
      ],
    );
  }
}
