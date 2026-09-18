import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 폼 위의 오류 한 줄. 웹 `FormError.tsx` 와 같은 모양이다.
///
/// `message` 가 `null` 이면 **아무것도 그리지 않는다** — 빈 상자가 자리를
/// 차지하면 오류가 뜰 때마다 폼이 들썩인다.
///
/// 문구는 서버가 준 것을 그대로 받는다. 백엔드 `ErrorCode` 의 message 가 이미
/// 한국어 사용자 문구이므로 **앱에서 감싸거나 접두어를 붙이지 마라** —
/// 같은 실패가 화면마다 다르게 보인다.
class FormError extends StatelessWidget {
  const FormError({super.key, required this.message});

  final String? message;

  @override
  Widget build(BuildContext context) {
    final message = this.message;
    if (message == null) return const SizedBox.shrink();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.red50,
        borderRadius: BorderRadius.circular(AppRadii.xl),
        border: Border.all(color: AppColors.red200),
      ),
      child: Text(
        message,
        style: const TextStyle(fontSize: 14, color: AppColors.red700),
      ),
    );
  }
}
