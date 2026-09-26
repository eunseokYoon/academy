import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/auth_redirect.dart';
import '../../core/theme/app_colors.dart';

/// 내 정보 화면 맨 아래 줄 — 왼쪽 개인정보처리방침, 오른쪽 로그아웃.
///
/// 학생 「내 정보 · 성적」(S-7)과 학부모 내 정보(P-5)가 같은 배치다(웹 주석).
/// **로그아웃은 그 역할의 유일한 경로다**(14-7) — 화면이 무엇을 못 받았든
/// 이 줄은 그린다.
class AccountFooter extends StatelessWidget {
  const AccountFooter({
    super.key,
    required this.logoutKey,
    required this.onLogout,
  });

  final Key logoutKey;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    const style = TextStyle(
      fontSize: 14,
      color: AppColors.slate500,
      decoration: TextDecoration.underline,
      decorationColor: AppColors.slate500,
    );
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        TextButton(
          key: const Key('privacy-link'),
          // push 라야 처리방침에서 뒤로 가면 여기로 돌아온다.
          onPressed: () => context.push(AppRoutes.privacy),
          child: const Text('개인정보처리방침', style: style),
        ),
        TextButton(
          key: logoutKey,
          onPressed: onLogout,
          child: const Text('로그아웃', style: style),
        ),
      ],
    );
  }
}
