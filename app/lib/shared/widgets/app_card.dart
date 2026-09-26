import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';

/// 웹의 `.card` — `rounded-2xl bg-white shadow-card`.
///
/// **Material `Card` 를 쓰지 마라.** 그쪽의 `elevation` 은 한 겹 회색 그림자라
/// paper 배경 위에서 따로 논다. 값은 [AppShadows.card] 한 곳에 있다.
class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(20),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        boxShadow: AppShadows.card,
      ),
      child: child,
    );
  }
}
