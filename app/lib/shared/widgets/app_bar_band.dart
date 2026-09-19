import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import 'logo.dart';

/// 학생·학부모가 공유하는 남색 앱바.
///
/// **아래 32px 은 장식이 아니라 자리다.** 홈의 지면(`.field`)과 첫 카드
/// (`.hero-lift`)가 여기까지 파고들어 앉는다 — 이 디자인의 시그니처다.
/// 줄이면 카드가 로고 줄을 덮는다.
///
/// **상단 탭 줄을 만들지 마라.** 웹의 `AppBar.tabs` 는 선생님 전용이고,
/// 학생·학부모는 하단 탭 바로 옮겼다 — 폰에서 화면 맨 위는 엄지가 안 닿고
/// 탭이 아홉이라 가로로 흘러 뒤쪽은 아무도 못 찾았다.
class AppBarBand extends StatelessWidget {
  const AppBarBand({super.key, required this.role});

  /// 워드마크 옆 칩. 「학생」 · 「학부모」
  final String role;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppColors.brand900,
      child: SafeArea(
        bottom: false,
        child: Padding(
          key: const Key('app-bar-padding'),
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12 + 32),
          child: Row(
            children: [
              const LogoBadge(),
              const SizedBox(width: 10),
              const Flexible(child: Wordmark()),
              const SizedBox(width: 8),
              Text(
                role,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: AppColors.brand300,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
