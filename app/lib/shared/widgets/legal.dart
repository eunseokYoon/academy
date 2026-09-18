import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

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

/// 상호·연락처·보관 기간 등 학원이 확정해야 하는 값이 남아 있다는 표시.
///
/// **그럴듯하게 채우지 마라.** 채우면 그것이 그대로 고지가 된다.
class LegalDraftNotice extends StatelessWidget {
  const LegalDraftNotice({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.amber50,
        borderRadius: BorderRadius.circular(AppRadii.lg),
      ),
      child: const Text(
        '초안입니다. 상호·연락처·보관 기간 등 최종 문구는 학원에서 확정한 뒤 반영합니다.',
        style: TextStyle(fontSize: 14, color: AppColors.amber900),
      ),
    );
  }
}

/// 학원이 값을 확정해야 하는 자리. 화면에서 눈에 띄게 남겨 둔다.
class Pending extends StatelessWidget {
  const Pending({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: AppColors.slate100,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        '[$label]',
        style: const TextStyle(
          fontSize: 12,
          fontFamily: 'monospace',
          color: AppColors.slate500,
        ),
      ),
    );
  }
}
