import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 구획의 색. **세 가지가 전부다.** 네 번째를 만들지 마라 — 색이 넷이 되는 순간
/// "이 색은 무슨 뜻이지"가 생기고, 그때부터 색이 정보를 못 준다.
///
/// * [accent](주황) — **학생이 아직 처리 안 한 것.** 미완료 숙제, 재제출.
///   `tailwind.config.js`의 accent 주석이 정본이다. 여기 말고 다른 구획에 쓰지 마라.
/// * [brand](남색) — 읽을 것·기록. 공지, 시험 일정, 성적, 출석.
/// * [neutral](회색) — 곁다리. 지난 수업, 자료실처럼 급하지 않은 것.
enum SectionTone { accent, brand, neutral }

/// 왼쪽 바 색(과 [TintBlock]의 왼쪽 띠 색). 웹 `Section.tsx`의 `BAR` 맵.
Color _barColor(SectionTone tone) => switch (tone) {
  SectionTone.accent => AppColors.accent500,
  SectionTone.brand => AppColors.brand600,
  SectionTone.neutral => AppColors.slate300,
};

/// 개수 필 배경. 웹의 `COUNT` 맵 — [_barColor]와 별개다.
///
/// 필은 흰 글씨를 얹으므로 바보다 한 단계 진해야 대비가 선다.
/// `neutral`에서만 갈라진다 — 바는 slate300, 필은 slate400.
Color _countColor(SectionTone tone) => switch (tone) {
  SectionTone.accent => AppColors.accent500,
  SectionTone.brand => AppColors.brand600,
  SectionTone.neutral => AppColors.slate400,
};

/// [TintBlock] 배경. 웹 `Section.tsx`의 `BLOCK` 맵 중 배경 값.
Color _tintBackground(SectionTone tone) => switch (tone) {
  SectionTone.accent => AppColors.accent50,
  SectionTone.brand => AppColors.brand50,
  SectionTone.neutral => Colors.white,
};

/// [TintBlock] 자식 사이 헤어라인 색. 웹 `BLOCK` 맵의 `[&>*+*]:border-*` 값.
Color _dividerColor(SectionTone tone) => switch (tone) {
  SectionTone.accent => AppColors.accent100,
  SectionTone.brand => AppColors.brand100,
  SectionTone.neutral => AppColors.slate100,
};

/// 구획 머리. 왼쪽 세로 바 + 제목 + 개수 필 + 「전체 ›」.
///
/// **이게 화면의 리듬을 만든다.** 예전에는 구획 제목이 회색 작은 글씨 하나뿐이라
/// 흰 카드가 세로로 쌓이면 어디서 구획이 끊기는지 글자를 읽어야 알 수 있었다.
/// 왼쪽 4px 바 하나로 그게 색이 된다.
///
/// `accent`는 「학생이 아직 처리해야 할 것」에만 쓴다 — 「안 낸 숙제」가 그것이다.
/// 공지·성적·출석 구획은 남색(brand)이다.
class SectionHead extends StatelessWidget {
  const SectionHead({
    super.key,
    this.tone = SectionTone.brand,
    required this.title,
    this.count,
    this.onTapAction,
    this.actionLabel = '전체',
  });

  final SectionTone tone;
  final String title;

  /// 제목 옆 알약. **null 이거나 0 이면 알약을 안 그린다** — 회색 0은
  /// "없음"을 굳이 강조한다. 그래서 개수를 걸러 넘길 필요가 없다(두 홈은
  /// 0 도 그대로 넘긴다).
  final int? count;

  /// 오른쪽 링크의 동작. null이면 안 그린다.
  final VoidCallback? onTapAction;
  final String actionLabel;

  @override
  Widget build(BuildContext context) {
    final count = this.count;
    final onTapAction = this.onTapAction;
    final barColor = _barColor(tone);

    return Padding(
      // 웹 `mb-2 pl-0.5`.
      padding: const EdgeInsets.only(left: 2, bottom: 8),
      child: Row(
        children: [
          Container(
            key: const Key('section-bar'),
            width: 4,
            height: 15,
            decoration: BoxDecoration(
              color: barColor,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 8),
          // 제목과 필을 Expanded 안에 한 덩어리로 둔다. Row에 Spacer를 바로 쓰면
          // Flexible 제목과 남은 폭을 반씩 나눠서, 자리가 충분한 제목도 두 줄로
          // 흐른다 — 웹의 `flex: 0 1 auto` + `ml-auto`와 다른 동작이다.
          Expanded(
            child: Row(
              children: [
                Flexible(
                  // overflow를 지정하지 마라 — CSS처럼 줄바꿈된다. 없으면 360px에서
                  // 긴 제목이 RenderFlex overflowed로 터진다.
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                      color: AppColors.brand900,
                      letterSpacing: -0.28,
                    ),
                  ),
                ),
                if (count != null && count > 0) ...[
                  const SizedBox(width: 8),
                  Container(
                    key: const Key('section-count'),
                    constraints: const BoxConstraints(minWidth: 20),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 1,
                    ),
                    decoration: BoxDecoration(
                      color: _countColor(tone),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      '$count',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (onTapAction != null)
            InkWell(
              onTap: onTapAction,
              child: Text(
                '$actionLabel ›',
                style: const TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                  color: AppColors.slate500,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 목록 한 덩어리. 흰 카드 여러 장 대신 **틴트 블록 한 장**이다.
///
/// 카드를 낱장으로 두면 항목이 6개일 때 같은 그림자가 6번 반복돼 리듬이
/// 사라진다. 한 덩어리로 묶고 안쪽은 헤어라인으로만 끊으면, 그림자는 한 번이고
/// 구획이 하나로 읽힌다. **자식 사이 선은 블록이 직접 긋는다** — 자식에게
/// 맡기지 마라.
class TintBlock extends StatelessWidget {
  const TintBlock({
    super.key,
    this.tone = SectionTone.brand,
    required this.children,
  });

  final SectionTone tone;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final stripeColor = _barColor(tone);
    final dividerColor = _dividerColor(tone);

    // 같은 Key 를 가진 형제가 한 부모(Column) 밑에 둘 이상이면 Flutter 가
    // "Duplicate keys found" 로 빌드를 막는다 — 그래서 선을 형제로 나란히
    // 두지 않고, 선+다음 자식을 한 겹 더 Column 으로 감싼다. 그러면 선은
    // 저마다 다른 부모 아래 하나씩만 있어 중복 검사에 걸리지 않으면서도,
    // find.byKey 로는 여전히 전부 찾힌다(트리 전체를 훑기 때문이다).
    final content = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i == 0) {
        content.add(children[i]);
      } else {
        content.add(
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(
                key: const Key('tint-divider'),
                height: 1,
                color: dividerColor,
              ),
              children[i],
            ],
          ),
        );
      }
    }

    return Container(
      key: const Key('tint-block'),
      // 둥근 모서리 밖으로 안쪽 헤어라인이 삐져나오지 않게 자른다.
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: _tintBackground(tone),
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        boxShadow: AppShadows.card,
      ),
      // Stack은 위치 지정 안 된 자식(Padding)의 높이를 따르고, 왼쪽 띠는
      // top:0/bottom:0으로 거기에 맞춰 늘어난다. IntrinsicHeight를 쓰지 마라.
      child: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: content,
            ),
          ),
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: 4,
            child: ColoredBox(
              key: const Key('tint-stripe'),
              color: stripeColor,
            ),
          ),
        ],
      ),
    );
  }
}
