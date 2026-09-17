import 'package:flutter/material.dart';

import '../../core/api/api_exception.dart';
import '../../core/theme/app_colors.dart';

/// 초기 비밀번호가 전원 `0000`이라, 바꾸기 전에는 세 경로 외 전부 403이다.
/// 그래서 라우터가 `mustChangePassword`에서 이 화면만 허용한다.
///
/// **정책은 8자 이상뿐이다.** 특수문자 강제 같은 제약을 넣지 마라 —
/// 학부모 연령대를 고려한 확정 사항이다.
class PasswordChangePage extends StatefulWidget {
  const PasswordChangePage({
    super.key,
    required this.onChange,
    required this.onLogout,
  });

  final Future<void> Function({
    required String currentPassword,
    required String newPassword,
  }) onChange;

  /// 이 화면이 막다른 길이 되지 않게 한다.
  final Future<void> Function() onLogout;

  @override
  State<PasswordChangePage> createState() => _PasswordChangePageState();
}

class _PasswordChangePageState extends State<PasswordChangePage> {
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;

    final current = _current.text;
    final next = _next.text;

    final complaint = switch (true) {
      _ when current.isEmpty => '지금 비밀번호를 입력해 주세요.',
      _ when next.length < 8 => '새 비밀번호는 8자 이상이어야 합니다.',
      _ when next != _confirm.text => '새 비밀번호가 서로 다릅니다.',
      _ when next == current => '지금 비밀번호와 다른 값을 넣어 주세요.',
      _ => null,
    };
    if (complaint != null) {
      setState(() => _error = complaint);
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      await widget.onChange(currentPassword: current, newPassword: next);
      // 성공하면 서버가 리프레시 토큰을 전부 폐기한다. 상태가 loggedOut 으로
      // 바뀌고 라우터가 로그인 화면으로 보낸다 — 여기서 이동하지 마라.
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('비밀번호 변경')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  '처음 비밀번호는 모두 0000 입니다.\n내 정보를 보려면 먼저 바꿔 주세요.',
                  style: TextStyle(height: 1.5, color: AppColors.brand900),
                ),
                const SizedBox(height: 20),
                _field('pw-current', _current, '지금 비밀번호'),
                _field('pw-new', _next, '새 비밀번호 (8자 이상)'),
                _field('pw-confirm', _confirm, '새 비밀번호 확인'),
                if (_error != null) ...[
                  const SizedBox(height: 4),
                  Text(_error!, style: const TextStyle(color: Color(0xFFB91C1C))),
                ],
                const SizedBox(height: 20),
                FilledButton(
                  key: const Key('pw-submit'),
                  onPressed: _submit,
                  child: _busy
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('바꾸기'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  key: const Key('pw-logout'),
                  onPressed: widget.onLogout,
                  child: const Text('로그아웃'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _field(String key, TextEditingController controller, String label) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        key: Key(key),
        controller: controller,
        obscureText: true,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }
}
