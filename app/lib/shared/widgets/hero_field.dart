import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 지면의 숫자 칸 하나. 값이 주인공이라 `value` 는 짧게 넣어라 —
/// 「D-3」·「2」·「08/15」. 문장을 넣으면 칸이 깨진다.
class HeroStat {
  const HeroStat({
    required this.label,
    required this.value,
    this.sub,
    this.extra,
    this.hot = false,
  });

  final String label;
  final String value;

  /// 값 아래 한 줄.
  final String? sub;

  /// 같은 칸 안의 둘째 줄. **칸을 넷으로 늘리는 대신 쓴다** — 360px 에서
  /// 칸이 넷이면 「D-61」이 줄바꿈된다. 다음 수업과 다음 클리닉처럼
  /// **같은 종류의 일정**일 때만 묶어라.
  final HeroStatExtra? extra;

  /// 주황 칸. **한 화면에 하나만.** 「학생이 아직 처리 안 한 것」에만 붙인다.
  final bool hot;
}

class HeroStatExtra {
  const HeroStatExtra({required this.label, required this.value, this.sub});

  final String label;
  final String value;
  final String? sub;
}

/// 홈 위쪽의 남색 지면. 앱바 띠가 그대로 아래로 이어져 하나로 읽힌다.
///
/// **왜 있나.** 화면 전체가 흰 카드 한 종류면 위계가 없다 — 인사말 카드와
/// 숙제 카드가 같은 흰색·그림자·반경이어서 어디가 본론인지 색으로 안 읽힌다.
/// 표면을 둘로 나누면 그 아래 카드가 「떠 있는 것」이 된다.
///
/// **아래 64px 은 다음 카드가 걸터앉을 자리다.** 화면 쪽에서 그 카드를 40
/// 올려야 한다(웹 `.hero-lift` = -mt-10). `Transform.translate` 는 그리기만
/// 옮기고 레이아웃은 안 옮기므로, 뒤따르는 형제 전부가 같은 누적 오프셋을
/// 받고 맨 끝 `SizedBox` 가 흡수한다.
///
/// **칸은 최대 셋.** 값이 없는 항목은 빼라 — null 을 「미정」으로 채우면
/// 없는 시험에 D-0 이 들어가 「시험이 오늘」로 읽힌다.
class HeroField extends StatelessWidget {
  const HeroField({
    super.key,
    this.eyebrow,
    required this.title,
    this.stats = const [],
    this.child,
  });

  /// 값 위의 작은 줄. 보통 오늘 날짜다.
  final String? eyebrow;

  /// 인사말. **`String` 이 아니라 [InlineSpan] 이다** — 웹은 인사말에서
  /// **이름 한 곳만** 주황(`text-accent-300`)이다. `tailwind.config.js` 의
  /// accent 주석이 이 색을 쓸 수 있는 자리를 다섯으로 못 박았고, 「장식으로 딱
  /// 세 곳」(워드마크의 LAB, **홈 인사말의 이름**, 주간 레포트의 「지금 보고
  /// 있는 주」) 중 하나가 여기다. 문자열로 받으면 그 한 곳이 사라지고, 학생이
  /// 가장 자주 여는 화면에 브랜드색이 한 점도 안 남는다.
  ///
  /// **서체·크기·기본색의 정본은 여전히 여기다** — [Text.rich] 가 아래
  /// `style` 을 스팬 트리 전체의 기본으로 적용하므로, 부르는 쪽은 한 단어의
  /// **색만** 덮는다. 크기·굵기를 화면에서 다시 적지 마라.
  final InlineSpan title;
  final List<HeroStat> stats;

  /// 칸 아래. 학부모 홈의 자녀 선택이 여기 들어간다.
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final eyebrow = this.eyebrow;
    final child = this.child;
    return Container(
      key: const Key('hero-field'),
      width: double.infinity,
      padding: const EdgeInsets.only(bottom: 64),
      decoration: const BoxDecoration(gradient: fieldGradient),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 384),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (eyebrow != null)
                  Text(
                    eyebrow,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: AppColors.brand200.withValues(alpha: 0.8),
                    ),
                  ),
                const SizedBox(height: 2),
                Text.rich(
                  title,
                  style: const TextStyle(
                    fontSize: 25,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                if (stats.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  IntrinsicHeight(
                    child: Row(
                      key: const Key('hero-stats'),
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        for (var i = 0; i < stats.length; i++) ...[
                          if (i > 0) const SizedBox(width: 10),
                          Expanded(
                            child: _Stat(index: i, stat: stats[i]),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
                if (child != null) ...[const SizedBox(height: 12), child],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.index, required this.stat});

  final int index;
  final HeroStat stat;

  @override
  Widget build(BuildContext context) {
    final sub = stat.sub;
    final extra = stat.extra;
    return Container(
      key: Key('stat-$index'),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        // 남색 위라 흰 카드를 얹지 않고 반투명 면을 판다 — 얹으면 지면이 사라진다.
        border: Border.all(
          color: stat.hot
              ? AppColors.accent300.withValues(alpha: 0.5)
              : Colors.white.withValues(alpha: 0.10),
        ),
        color: stat.hot
            ? AppColors.accent400.withValues(alpha: 0.22)
            : Colors.white.withValues(alpha: 0.07),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          _Line(label: stat.label, value: stat.value, sub: sub, hot: stat.hot),
          if (extra != null) ...[
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Divider(
                color: AppColors.brand200.withValues(alpha: 0.25),
                height: 2,
              ),
            ),
            const SizedBox(height: 2),
            _Line(
              label: extra.label,
              value: extra.value,
              sub: extra.sub,
              hot: false,
            ),
          ],
        ],
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line({
    required this.label,
    required this.value,
    this.sub,
    this.hot = false,
  });

  final String label;
  final String value;
  final String? sub;
  final bool hot;

  @override
  Widget build(BuildContext context) {
    final sub = this.sub;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 10.5,
            fontWeight: FontWeight.w600,
            color: hot
                ? AppColors.accent200
                : AppColors.brand200.withValues(alpha: 0.75),
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontSize: 21,
            fontWeight: FontWeight.w800,
            color: hot ? AppColors.accent100 : Colors.white,
            fontFeatures: kTabularFigures,
          ),
        ),
        if (sub != null)
          Text(
            sub,
            style: TextStyle(
              fontSize: 10.5,
              color: AppColors.brand200.withValues(alpha: 0.55),
              fontFeatures: kTabularFigures,
            ),
          ),
      ],
    );
  }
}
