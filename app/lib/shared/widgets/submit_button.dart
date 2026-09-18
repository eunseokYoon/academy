import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 폼 제출 버튼. 웹 `SubmitButton.tsx` 와 같다.
///
/// **대기 중에는 라벨이 「처리 중…」 으로 바뀐다** — 스피너가 아니라 글자인 것도
/// 웹을 따른다. 그리고 그때 눌리지 않는다: 두 번 나가면 리프레시 토큰이 두 개
/// 발급되거나 계정이 중복 생성 시도된다.
///
/// 색은 `brand600` 이다. **`accent`(주황)를 쓰지 마라** — 주황은
/// 「아직 안 한 것」 또는 「지금 여기」만 뜻하고, 눌러서 뭔가 되는 곳까지 주황이면
/// amber(경고)와 red(위험)가 뜻하던 것이 흐려진다.
class SubmitButton extends StatelessWidget {
  const SubmitButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.pending = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool pending;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadii.xl),
        boxShadow: pending || onPressed == null ? const [] : AppShadows.card,
      ),
      child: FilledButton(
        onPressed: pending ? null : onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.brand600,
          disabledBackgroundColor: AppColors.slate300,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadii.xl),
          ),
        ),
        child: Text(
          pending ? '처리 중…' : label,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}
