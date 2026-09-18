import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// 로그인 판정이 끝나기 전에 보여준다. 이게 없으면 로그인 화면이 한 번 깜빡인다.
///
/// 스피너가 아니라 글자인 것은 웹과 같다 — 짧게 지나가는 화면에 스피너를 두면
/// 깜빡임이 더 눈에 띈다.
class FullScreenLoader extends StatelessWidget {
  const FullScreenLoader({super.key});

  @override
  Widget build(BuildContext context) {
    return const ColoredBox(
      color: AppColors.paper,
      child: Center(
        child: Text(
          '불러오는 중…',
          style: TextStyle(fontSize: 14, color: AppColors.slate400),
        ),
      ),
    );
  }
}
