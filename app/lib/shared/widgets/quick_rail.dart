import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../icons/app_icon.dart';
import '../icons/icon_paths.dart';

/// 레일의 칸 하나.
class QuickItem {
  const QuickItem({
    required this.route,
    required this.icon,
    required this.label,
    this.count = 0,
    this.primary = false,
  });

  final String route;
  final AppIconName icon;
  final String label;

  /// 0보다 크면 칸 위에 점이 붙는다. **숫자는 쓰지 않는다** — 52px 칸 위의
  /// 「3」은 읽으려고 눈이 멈추는데, 알아야 하는 건 「볼 게 있다」뿐이다.
  final int count;

  /// 남색으로 채운 칸. **레일당 하나만.** 둘이면 「여기부터 보면 된다」가
  /// 사라지고 칸들이 도로 똑같아진다 — 옅은 칸이 가로로 늘어서면 아이콘
  /// 모양만 다르고 무게가 같아서 어디를 눌러야 하는지 라벨을 읽어야 나온다.
  final bool primary;
}

/// 홈의 가로 퀵 레일. **홈을 뺀 모든 화면이 여기 다 있다.**
///
/// 하단 탭 바와 겹치는 항목이 있는 것은 의도다 — 하단 바는 자주 쓰는 곳의
/// 빠른 길이고 이 레일은 「전부 한눈에」가 목적이라 역할이 다르다.
/// 겹친다고 빼면 레일이 남은 것들의 잡동사니가 된다.
///
/// **마지막 칸이 화면 밖으로 반쯤 나가야 한다.** 잘린 원이 이 레일의 유일한
/// 어포던스다 — 폭에 딱 맞으면 옆으로 넘길 수 있다는 걸 아무도 모른다.
/// 그래서 **감싸는 카드는 가로 패딩을 주지 않고**, 레일이 자기 여백을 갖는다.
class QuickRail extends StatelessWidget {
  const QuickRail({super.key, required this.items, required this.onTap});

  final List<QuickItem> items;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 76,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: items.length,
        separatorBuilder: (_, _) => const SizedBox(width: 12),
        itemBuilder: (_, i) => _Tile(item: items[i], onTap: onTap),
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({required this.item, required this.onTap});

  final QuickItem item;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    final primary = item.primary;
    return SizedBox(
      width: 52,
      child: InkWell(
        onTap: () => onTap(item.route),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  key: Key('rail-${item.route}'),
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    color: primary ? AppColors.brand900 : AppColors.brand50,
                    border: primary
                        ? null
                        : Border.all(width: 1, color: AppColors.brand100),
                  ),
                  child: Center(
                    child: AppIcon(
                      item.icon,
                      size: 22,
                      color: primary ? Colors.white : AppColors.brand600,
                    ),
                  ),
                ),
                if (item.count > 0)
                  Positioned(
                    top: 2,
                    right: 2,
                    child: Container(
                      key: Key('dot-${item.route}'),
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.accent500,
                        border: Border.all(width: 2, color: Colors.white),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              item.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w500,
                color: AppColors.brand900,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
