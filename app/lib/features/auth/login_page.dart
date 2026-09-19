import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/lib/phone.dart';
import 'package:academy_app/shared/widgets/app_card.dart';
import 'package:academy_app/shared/widgets/app_text_field.dart';
import 'package:academy_app/shared/widgets/form_error.dart';
import 'package:academy_app/shared/widgets/logo.dart';
import 'package:academy_app/shared/widgets/submit_button.dart';

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
    if (_busy) return;

    final loginId = digitsOnly(_id.text);
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
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(() => _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.brand900,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 384),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(
                    child: LogoBadge(
                      size: 68,
                      markSize: 42,
                      radius: 20,
                      borderWidth: 2,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Center(child: Wordmark(fontSize: 20)),
                  const SizedBox(height: 24),
                  AppCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Text(
                          '로그인',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: AppColors.brand900,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          '전화번호로 로그인합니다.',
                          style: TextStyle(
                            fontSize: 14,
                            color: AppColors.slate500,
                          ),
                        ),
                        const SizedBox(height: 20),
                        AppTextField(
                          key: const Key('login-id'),
                          label: '전화번호',
                          controller: _id,
                          placeholder: '010-1234-5678',
                          keyboardType: TextInputType.phone,
                          inputFormatters: [PhoneInputFormatter()],
                        ),
                        const SizedBox(height: 16),
                        AppTextField(
                          key: const Key('login-password'),
                          label: '비밀번호',
                          controller: _password,
                          obscureText: true,
                          onSubmitted: (_) => _submit(),
                        ),
                        const SizedBox(height: 16),
                        FormError(message: _error),
                        if (_error != null) const SizedBox(height: 16),
                        SubmitButton(
                          key: const Key('login-submit'),
                          label: '로그인',
                          pending: _busy,
                          onPressed: _submit,
                        ),
                        const SizedBox(height: 20),
                        const Divider(
                          height: 1,
                          thickness: 1,
                          color: AppColors.slate100,
                        ),
                        const SizedBox(height: 16),
                        const _SignupLine(),
                        const SizedBox(height: 6),
                        const Text(
                          '비밀번호를 잊으셨나요? 선생님께 문의해 주세요.',
                          style: TextStyle(
                            fontSize: 14,
                            color: AppColors.slate500,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 12,
                    children: [
                      _LegalLink(
                        label: '이용약관',
                        onTap: () => context.go(AppRoutes.terms),
                      ),
                      _LegalLink(
                        label: '개인정보처리방침',
                        onTap: () => context.go(AppRoutes.privacy),
                      ),
                    ],
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

class _SignupLine extends StatelessWidget {
  const _SignupLine();

  @override
  Widget build(BuildContext context) {
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        const Text(
          '처음이신가요? ',
          style: TextStyle(fontSize: 14, color: AppColors.slate500),
        ),
        GestureDetector(
          key: const Key('login-to-signup'),
          onTap: () => context.go(AppRoutes.signup),
          child: const Text(
            '회원가입',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.brand600,
              decoration: TextDecoration.underline,
              decorationColor: AppColors.brand600,
            ),
          ),
        ),
      ],
    );
  }
}

class _LegalLink extends StatelessWidget {
  const _LegalLink({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          color: Colors.white.withValues(alpha: 0.7),
          decoration: TextDecoration.underline,
          decorationColor: Colors.white.withValues(alpha: 0.7),
        ),
      ),
    );
  }
}
