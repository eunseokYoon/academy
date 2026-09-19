import 'dart:ui';

import 'package:flutter/material.dart';

import '../../core/router/routes.dart';
import '../../core/theme/app_colors.dart';
import '../icons/app_icon.dart';

/// 엄지 자리에 고정된 하단 탭 바. 학생·학부모의 **주 내비게이션**이다.
///
/// **`BottomNavigationBar`·`NavigationBar` 를 쓰지 마라.** 현재 탭은 색만
/// 바뀌는 게 아니라 아이콘 선 굵기(1.7→2.2)와 라벨 굵기(w500→w700)까지
/// 바뀐다 — 색각 이상이 있거나 화면이 밝은 야외에서 색만으로는 어디 있는지
/// 알 수 없어서 웹이 일부러 넣은 장치다. Material 위젯은 선 굵기를 못 바꾼다.
///
/// **스크롤되면 안 된다. 그래서 다섯까지다.** 하단 바의 값어치는 「숙제는 항상
/// 왼쪽에서 두 번째」라는 위치 기억이고, 옆으로 넘어가는 순간 사라진다.
///
/// 현재 위치의 주황은 「지금 여기」다 — amber(경고)·red(위험)와 부딪히지 않는다.
/// 버튼·링크로 넓히지 마라.
class BottomTabBar extends StatelessWidget {
  const BottomTabBar({
    super.key,
    required this.tabs,
    required this.currentIndex,
    required this.onTap,
  });

  final List<BottomTab> tabs;
  final int currentIndex;
  final ValueChanged<int> onTap;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.95),
        border: Border(
          top: BorderSide(color: AppColors.slate200.withValues(alpha: 0.8)),
        ),
      ),
      child: ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
          child: SafeArea(
            top: false,
            child: SizedBox(
              height: 56,
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 384),
                  child: Row(
                    children: [
                      for (var i = 0; i < tabs.length; i++)
                        Expanded(
                          child: _Tab(
                            key: ValueKey('tab-${tabs[i].route}'),
                            tab: tabs[i],
                            active: i == currentIndex,
                            onTap: () => onTap(i),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Tab extends StatelessWidget {
  const _Tab({
    super.key,
    required this.tab,
    required this.active,
    required this.onTap,
  });

  final BottomTab tab;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = active ? AppColors.accent500 : AppColors.slate400;
    return InkWell(
      onTap: onTap,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          AppIcon(
            tab.icon,
            size: 22,
            strokeWidth: active ? 2.2 : 1.7,
            color: color,
          ),
          const SizedBox(height: 4),
          Text(
            tab.label,
            style: TextStyle(
              fontSize: 10,
              height: 1,
              fontWeight: active ? FontWeight.w700 : FontWeight.w500,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}
