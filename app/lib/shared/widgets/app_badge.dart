import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 배지의 색. 웹 `Badge.tsx` 의 `Tone` 다섯이 전부다 — **새 값을 만들지 마라.**
/// 배지가 아는 색만 있어야 색이 뜻을 가진다.
enum BadgeTone { neutral, ok, warn, danger, brand }

/// 옅은 배경 + 같은 계열 테두리. 웹 `Badge.tsx` 그대로다.
///
/// 테두리가 없으면 흰 카드 위에서 배지가 번져 보인다. **상태색을 브랜드 남색으로
/// 바꾸지 마라** — 포인트가 남색으로 옮겨간 덕분에 빨강이 「결석·위험」 한
/// 가지 뜻만 갖게 됐다.
///
/// Material 의 `Badge` 와 이름이 겹쳐서 `AppBadge` 다.
class AppBadge extends StatelessWidget {
  const AppBadge({
    super.key,
    this.tone = BadgeTone.neutral,
    required this.label,
  });

  final BadgeTone tone;
  final String label;

  @override
  Widget build(BuildContext context) {
    final (background, foreground, ring) = switch (tone) {
      BadgeTone.neutral => (
        AppColors.slate100,
        AppColors.slate600,
        AppColors.slate200,
      ),
      BadgeTone.ok => (
        AppColors.emerald50,
        AppColors.emerald700,
        AppColors.emerald200,
      ),
      BadgeTone.warn => (
        AppColors.amber50,
        AppColors.amber700,
        AppColors.amber200,
      ),
      BadgeTone.danger => (AppColors.red50, AppColors.red700, AppColors.red200),
      BadgeTone.brand => (
        AppColors.brand50,
        AppColors.brand700,
        AppColors.brand200,
      ),
    };
    return Container(
      // 웹 `px-1.5 py-0.5 rounded-md ring-1 ring-inset`.
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: ring),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: foreground,
          fontFeatures: kTabularFigures,
        ),
      ),
    );
  }
}
