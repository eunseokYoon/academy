import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';

/// 로그인 아이디는 전화번호다(`users.login_id = phone`, 숫자만).
/// 비밀번호 찾기는 없다 — 선생님이 초기화해 준다.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.onLogin});

  final Future<void> Function({
    required String loginId,
    required String password,
  })
  onLogin;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _id = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _id.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    // 보내는 동안 다시 누르면 리프레시 토큰이 두 개 발급된다.
    if (_busy) return;

    final loginId = _id.text.replaceAll(RegExp(r'[^0-9]'), '');
    if (loginId.isEmpty) {
      setState(() => _error = '전화번호를 입력해 주세요.');
      return;
    }
    if (_password.text.isEmpty) {
      setState(() => _error = '비밀번호를 입력해 주세요.');
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      await widget.onLogin(loginId: loginId, password: _password.text);
      // 화면 이동은 라우터가 한다. 여기서 go 하지 마라 — 상태가 바뀌면
      // refreshListenable 이 리다이렉트를 일으킨다.
    } on ApiException catch (e) {
      // 서버 문구를 그대로 쓴다. 앱에서 문구를 다시 만들지 마라.
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
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    '학원',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      color: AppColors.brand900,
                    ),
                  ),
                  const SizedBox(height: 32),
                  TextField(
                    key: const Key('login-id'),
                    controller: _id,
                    keyboardType: TextInputType.phone,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'[0-9-]')),
                    ],
                    decoration: const InputDecoration(
                      labelText: '전화번호',
                      hintText: '01012345678',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    key: const Key('login-password'),
                    controller: _password,
                    obscureText: true,
                    onSubmitted: (_) => _submit(),
                    decoration: const InputDecoration(labelText: '비밀번호'),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(
                      _error!,
                      style: const TextStyle(color: Color(0xFFB91C1C)),
                    ),
                  ],
                  const SizedBox(height: 20),
                  FilledButton(
                    key: const Key('login-submit'),
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
                        : const Text('로그인'),
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    key: const Key('login-to-signup'),
                    onPressed: () => context.go(AppRoutes.signup),
                    child: const Text('반 코드로 가입하기'),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    '비밀번호를 잊으셨으면 선생님께 말씀해 주세요.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
