import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/router/auth_redirect.dart';
import '../../core/theme/app_colors.dart';
import 'app_text_field.dart';
import 'form_error.dart';

/// 내 정보 화면 맨 아래 줄 — 왼쪽 개인정보처리방침, 오른쪽 계정 삭제·로그아웃.
///
/// 학생 「내 정보 · 성적」(S-7)과 학부모 내 정보(P-5)가 같은 배치다(웹 주석).
/// **로그아웃은 그 역할의 유일한 경로다**(14-7) — 화면이 무엇을 못 받았든
/// 이 줄은 그린다. 계정 삭제(2026-10-03)도 스토어가 요구하는 앱 안 경로라 같은 줄에 둔다.
class AccountFooter extends StatelessWidget {
  const AccountFooter({
    super.key,
    required this.logoutKey,
    required this.onLogout,
    required this.onDeleteAccount,
  });

  final Key logoutKey;
  final Future<void> Function() onLogout;

  /// 비밀번호를 받아 계정을 지운다. 실패하면 던진다(대화상자가 서버 문구를 띄운다).
  final Future<void> Function(String password) onDeleteAccount;

  @override
  Widget build(BuildContext context) {
    const style = TextStyle(
      fontSize: 14,
      color: AppColors.slate500,
      decoration: TextDecoration.underline,
      decorationColor: AppColors.slate500,
    );
    const quiet = TextStyle(
      fontSize: 14,
      color: AppColors.slate400,
      decoration: TextDecoration.underline,
      decorationColor: AppColors.slate400,
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
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextButton(
              key: const Key('account-delete'),
              onPressed: () => showDialog<void>(
                context: context,
                builder: (_) => AccountDeleteDialog(onDelete: onDeleteAccount),
              ),
              child: const Text('계정 삭제', style: quiet),
            ),
            TextButton(
              key: logoutKey,
              onPressed: onLogout,
              child: const Text('로그아웃', style: style),
            ),
          ],
        ),
      ],
    );
  }
}

/// 계정 삭제 확인. 웹 `AccountDeleteButton.tsx` 와 같은 문구다.
///
/// 성공하면 인증 상태가 `loggedOut` 이 되어 라우터가 로그인 화면으로 옮긴다 — 이 대화상자는
/// 그때 함께 사라진다. 아직 남아 있으면 닫는다.
class AccountDeleteDialog extends StatefulWidget {
  const AccountDeleteDialog({super.key, required this.onDelete});

  final Future<void> Function(String password) onDelete;

  @override
  State<AccountDeleteDialog> createState() => _AccountDeleteDialogState();
}

class _AccountDeleteDialogState extends State<AccountDeleteDialog> {
  final _password = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final password = _password.text;
    if (password.isEmpty || _pending) return;
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.onDelete(password);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _pending = false;
        _error = e is ApiException ? e.message : '계정을 삭제하지 못했습니다. 연결을 확인해 주세요.';
      });
      return;
    }
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    const body = TextStyle(fontSize: 14, height: 1.5, color: AppColors.slate700);
    const sub = TextStyle(fontSize: 13, height: 1.5, color: AppColors.slate500);
    return AlertDialog(
      title: const Text('계정 삭제'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('로그인 계정(아이디·비밀번호)이 바로 지워지고 되돌릴 수 없습니다.', style: body),
            const SizedBox(height: 6),
            const Text(
              '수업에서 쌓인 출석·숙제·성적 기록은 학원 운영을 위해 개인정보처리방침에 적힌 기간 동안 보관된 뒤 삭제됩니다.',
              style: sub,
            ),
            const SizedBox(height: 16),
            AppTextField(
              key: const Key('account-delete-password'),
              label: '비밀번호 확인',
              controller: _password,
              obscureText: true,
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              FormError(key: const Key('account-delete-error'), message: _error),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _pending ? null : () => Navigator.of(context).pop(),
          child: const Text('취소'),
        ),
        TextButton(
          key: const Key('account-delete-confirm'),
          onPressed: _pending ? null : _submit,
          child: Text(_pending ? '삭제 중…' : '계정 삭제'),
        ),
      ],
    );
  }
}
