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
    this.hot = false,
  });

  final String label;
  final String value;

  /// 값 아래 한 줄.
  final String? sub;

  /// 주황 칸. **한 화면에 하나만.** 「학생이 아직 처리 안 한 것」에만 붙인다.
  final bool hot;
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
/// **윗줄은 일정 칸(D-day) 최대 셋, 아랫줄은 폭 전체의 [banner] 하나다(2026-09-29,
/// 앱만).** 다음 수업·다음 클리닉·시험이 나란히, 미완료 숙제가 그 밑이다. 웹은 아직
/// 한 줄 셋(수업+클리닉 묶음 · 숙제 · 시험)이다. **윗줄을 넷으로 늘리지 마라** —
/// 360px 에서 「D-61」이 줄바꿈된다. 값이 없는 항목은 빼라 — null 을 「미정」으로
/// 채우면 없는 시험에 D-0 이 들어가 「시험이 오늘」로 읽힌다.
class HeroField extends StatelessWidget {
  const HeroField({
    super.key,
    this.eyebrow,
    required this.title,
    this.stats = const [],
    this.banner,
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

  /// 일정 칸 밑의 넓은 칸. 미완료 숙제다.
  final HeroStat? banner;

  /// 칸 아래. 학부모 홈의 자녀 선택이 여기 들어간다. **위 여백은 자식 몫이다.**
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final eyebrow = this.eyebrow;
    final child = this.child;
    final banner = this.banner;
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
                if (banner != null) ...[
                  SizedBox(height: stats.isEmpty ? 16 : 10),
                  _Banner(stat: banner),
                ],
                // **사이 여백을 여기서 주지 마라.** 웹 `HeroField.tsx` 는
                // `{children}` 을 칸 줄 바로 뒤에 그대로 두고, 여백은 자식이
                // 자기 `mt-4` 로 갖는다(학부모 홈의 자녀 선택). 여기서 12 를
                // 더하면 자식의 16 과 겹쳐 28 이 된다.
                ?child,
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
  Widget build(BuildContext context) => Container(
    key: Key('stat-$index'),
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
    decoration: _cellDecoration(stat.hot),
    child: _Line(
      label: stat.label,
      value: stat.value,
      sub: stat.sub,
      hot: stat.hot,
    ),
  );
}

/// 폭 전체 칸. 라벨·설명이 왼쪽, 값이 오른쪽이다 — 세로로 쌓으면 넓은 칸이 키만
/// 커지고 윗줄 일정 칸보다 무거워 보인다.
class _Banner extends StatelessWidget {
  const _Banner({required this.stat});

  final HeroStat stat;

  @override
  Widget build(BuildContext context) {
    final sub = stat.sub;
    final hot = stat.hot;
    return Container(
      key: const Key('stat-banner'),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: _cellDecoration(hot),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(stat.label, style: _labelStyle(hot)),
                if (sub != null) Text(sub, style: _subStyle),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(stat.value, style: _valueStyle(hot)),
        ],
      ),
    );
  }
}

// 남색 위라 흰 카드를 얹지 않고 반투명 면을 판다 — 얹으면 지면이 사라진다.
BoxDecoration _cellDecoration(bool hot) => BoxDecoration(
  borderRadius: BorderRadius.circular(AppRadii.xxl),
  border: Border.all(
    color: hot
        ? AppColors.accent300.withValues(alpha: 0.5)
        : Colors.white.withValues(alpha: 0.10),
  ),
  color: hot
      ? AppColors.accent400.withValues(alpha: 0.22)
      : Colors.white.withValues(alpha: 0.07),
);

TextStyle _labelStyle(bool hot) => TextStyle(
  fontSize: 10.5,
  fontWeight: FontWeight.w600,
  color: hot ? AppColors.accent200 : AppColors.brand200.withValues(alpha: 0.75),
);

TextStyle _valueStyle(bool hot) => TextStyle(
  fontSize: 21,
  fontWeight: FontWeight.w800,
  color: hot ? AppColors.accent100 : Colors.white,
  fontFeatures: kTabularFigures,
);

final _subStyle = TextStyle(
  fontSize: 10.5,
  color: AppColors.brand200.withValues(alpha: 0.55),
  fontFeatures: kTabularFigures,
);

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
        Text(label, style: _labelStyle(hot)),
        Text(value, style: _valueStyle(hot)),
        if (sub != null) Text(sub, style: _subStyle),
      ],
    );
  }
}
