import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// 약관·처방침의 한 절. 웹 `LegalLayout.tsx` 와 같다.
class LegalSection extends StatelessWidget {
  const LegalSection({super.key, required this.title, required this.body});

  final String title;
  final Widget body;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.slate900,
            ),
          ),
          const SizedBox(height: 4),
          DefaultTextStyle(
            style: const TextStyle(
              fontSize: 14,
              height: 1.6,
              color: AppColors.slate600,
            ),
            child: body,
          ),
        ],
      ),
    );
  }
}

/// 약관·처리방침 하단, 로그인으로 돌아가는 링크. `TermsPage`·`PrivacyPage`
/// 둘이 그대로 썼었다 — 여기 하나로 합친다.
///
/// 로그인한 채 내 정보에서 열었으면(쌓여 있으면) 「돌아가기」로 그 화면에 돌아간다.
class BackLink extends StatelessWidget {
  const BackLink({super.key, required this.onTap, this.label = '로그인으로 돌아가기'});

  final VoidCallback onTap;
  final String label;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 14,
          color: AppColors.slate500,
          decoration: TextDecoration.underline,
        ),
      ),
    );
  }
}
