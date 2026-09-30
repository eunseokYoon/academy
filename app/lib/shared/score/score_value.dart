import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';
import 'score_data.dart';

/// 성적 한 칸의 글자. 성적 목록(S-7 · P-4)과 주간 레포트(P-6)가 같이 쓴다 —
/// 두 화면이 각자 만들면 같은 성적이 한 곳에서는 「23/25」, 다른 곳에서는
/// 「92점」이 된다. 웹 정본은 `shared/components/ScoreValue.tsx`.
///
/// **순서가 중요하다** — 클리닉은 `correctCount` 가 비어 있으므로 내부·외부를
/// 먼저 본다. 리뷰는 둘 다 비어 있어 null 이다(배지만 남는다).
///
/// 정답률·환산 점수는 없앴다(2026-09-10). 되살리지 마라.
String? scoreValueText(ScoreItem item) {
  if (item.internalCorrect != null || item.externalCorrect != null) {
    String n(int? v) => v?.toString() ?? '—';
    return '내부 ${n(item.internalCorrect)}/${n(item.internalTotal)} · '
        '외부 ${n(item.externalCorrect)}/${n(item.externalTotal)}';
  }
  final correct = item.correctCount;
  if (correct != null) return '$correct/${item.totalCount}';
  return null;
}

/// 통과 / 재시험 예정 / 재시험 통과. **판정은 서버 값을 그대로 쓴다.**
///
/// 「재시험 미통과」 상태는 없다 — 또 떨어지면 선생님이 체크를 안 하므로
/// 「재시험 예정」이 유지된다. `retestScheduled` 를 먼저 보는 이유는 예정·통과
/// 둘 다 `result` 가 FAIL 이기 때문이다.
///
/// 「재시험 예정」의 빨강은 웹 그대로다(구획 머리의 주황 「재시험 예정」과 별개).
class ScoreResultBadge extends StatelessWidget {
  const ScoreResultBadge({super.key, required this.item});

  final ScoreItem item;

  @override
  Widget build(BuildContext context) {
    if (item.result == null) return const SizedBox.shrink();
    final (label, bg, fg) = item.retestScheduled
        ? ('재시험 예정', AppColors.red100, AppColors.red700)
        : item.retestPassed
        ? ('재시험 통과', AppColors.blue100, AppColors.blue700)
        : ('통과', AppColors.emerald100, AppColors.emerald700);
    return Container(
      key: const Key('score-result'),
      margin: const EdgeInsets.only(left: 8),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        softWrap: false,
        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: fg),
      ),
    );
  }
}

/// 값 + 배지 한 덩어리(오른쪽 정렬). 줄 오른쪽에 둔다.
class ScoreValueWithBadge extends StatelessWidget {
  const ScoreValueWithBadge({super.key, required this.item});

  final ScoreItem item;

  @override
  Widget build(BuildContext context) {
    // 빨강이 아니다 — 빨강은 결석·위험 하나다. 안 본 시험은 회색으로 사실만 알린다(웹과 같다).
    if (item.absent) {
      // 줄 오른쪽에 붙인다. Expanded 안이라 감싸지 않으면 알약이 줄 폭만큼 늘어난다.
      return Align(
        alignment: Alignment.centerRight,
        child: Container(
          key: const Key('score-absent'),
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(
            color: AppColors.slate100,
            borderRadius: BorderRadius.circular(999),
          ),
          child: const Text(
            '미응시',
            softWrap: false,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.slate600,
            ),
          ),
        ),
      );
    }
    final text = scoreValueText(item);
    return Wrap(
      alignment: WrapAlignment.end,
      crossAxisAlignment: WrapCrossAlignment.center,
      runSpacing: 4,
      children: [
        if (text != null)
          Text(
            text,
            style: const TextStyle(
              fontSize: 14,
              color: AppColors.slate700,
              fontFeatures: kTabularFigures,
            ),
          ),
        ScoreResultBadge(item: item),
      ],
    );
  }
}
